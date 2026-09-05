#!/usr/bin/env bash
#
# Manage third-party frontend libraries vendored under public/vendor/.
#
# Libraries are registered below; each entry carries its upstream source type
# so the script can query the latest version ("check") and pull a pinned
# version / the latest one ("update"). Files are stored in
# public/vendor/<lib>/ together with a VERSION marker and are meant to be
# committed to the repository -- this script is only run manually when you
# want to install or upgrade a library, never during builds or deploys.
# Downloaded files are stored byte-for-byte identical to upstream.
#
# Usage:
#   bin/vendor-libs.sh [command] [lib] [version]
#
# Commands:
#   status              show installed version of every library
#   check [lib]         show installed vs. latest upstream (new-version check)
#   update [lib] [ver]  fetch a library. Without a version the latest
#                       upstream is used. Without a lib, all libraries update.
#                       A specific version/ref can be given to pin one library.
#
# Examples:
#   bin/vendor-libs.sh check
#   bin/vendor-libs.sh update proj4
#   bin/vendor-libs.sh update eviltransform 03ba58d92dfda57f8a1635f3805483c8fc10bd77
#
# Environment (only needed where the defaults are unreachable):
#   RAW_BASE  base URL for raw.githubusercontent.com (mirror, e.g.
#             https://ghfast.top/https://raw.githubusercontent.com)
#   GIT_BASE  base host for git ls-remote (mirror, e.g. https://ghfast.top/https://github.com)
#   NPM_BASE  base URL of the npm CDN (mirror, e.g. https://cdn.jsdelivr.net/npm)
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENDOR_DIR="$ROOT/public/vendor"

RAW_BASE="${RAW_BASE:-https://raw.githubusercontent.com}"
GIT_BASE="${GIT_BASE:-https://github.com}"
NPM_BASE="${NPM_BASE:-https://unpkg.com}"

# lib | source | upstream | subpath | installed file
#   source=github: upstream is "owner/repo", subpath inside repo,
#                  VERSION stores the commit hash.
#   source=npm:    upstream is the npm package name, subpath inside package,
#                  VERSION stores the semantic version.
LIBS=(
    "eviltransform|github|googollee/eviltransform|javascript/transform.js|transform.js"
    "proj4|npm|proj4|dist/proj4.js|proj4.js"
)

say() { printf '\033[1;32m%s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m%s\033[0m\n' "$*" >&2; }
err() { printf '\033[1;31m%s\033[0m\n' "$*" >&2; }

installed_version() {
    local lib="$1"
    [[ -f "$VENDOR_DIR/$lib/VERSION" ]] && cat "$VENDOR_DIR/$lib/VERSION" || echo "(not installed)"
}

latest_github() { # owner/repo -> commit hash
    git ls-remote "$GIT_BASE/$1.git" HEAD 2>/dev/null | awk '{print $1; exit}'
}

latest_npm() { # package -> latest version
    curl -fsSL --max-time 30 "$NPM_BASE/$1/package.json" 2>/dev/null \
        | sed -n 's/^[[:space:]]*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1
}

verify_eviltransform() { grep -q 'window\["eviltransform"\]' "$1"; }
verify_proj4() { grep -q '"undefined"!=typeof module?module.exports=s()' "$1"; }

update_lib() {
    local name="$1" ver src url subpath file dest line
    line="$(printf '%s\n' "${LIBS[@]}" | awk -F'|' -v n="$name" '$1==n')"
    IFS='|' read -r _ src url subpath file <<< "$line"
    if [[ -z "$src" ]]; then err "unknown library: $name"; exit 1; fi

    if [[ "$src" == "github" ]]; then
        ver="${2:-$(latest_github "$url")}"
        [[ -z "$ver" ]] && { err "cannot resolve latest revision of $url"; exit 1; }
    else
        ver="${2:-$(latest_npm "$url")}"
        [[ -z "$ver" ]] && { err "cannot resolve latest version of $url"; exit 1; }
    fi

    dest="$VENDOR_DIR/$name/$file"
    mkdir -p "$(dirname "$dest")"
    tmp="$(mktemp)"   # global on purpose: the EXIT trap runs after locals are gone
    trap 'rm -f "$tmp"' EXIT

    say "==> $name: fetching ${ver:0:12}"
    if [[ "$src" == "github" ]]; then
        curl -fsSL --max-time 60 "$RAW_BASE/$url/$ver/$subpath" -o "$tmp"
    else
        curl -fsSL --max-time 60 "$NPM_BASE/$url@$ver/$subpath" -o "$tmp"
    fi
    [[ -s "$tmp" ]] || { err "download failed (empty file)"; exit 1; }

    case "$name" in
        eviltransform) verify_eviltransform "$tmp" || { err "content check failed for eviltransform"; exit 1; } ;;
        proj4)         verify_proj4 "$tmp"         || { err "content check failed for proj4"; exit 1; } ;;
    esac

    cp "$tmp" "$dest"
    echo "$ver" > "$VENDOR_DIR/$name/VERSION"
    say "==> $name: installed $(wc -c < "$dest") bytes, version ${ver:0:12}"
}

show_status() {
    printf '%-14s %-12s %s\n' 'LIBRARY' 'INSTALLED' 'LATEST'
    for entry in "${LIBS[@]}"; do
        IFS='|' read -r name src url _ <<< "$entry"
        local latest
        if [[ "$src" == "github" ]]; then latest="$(latest_github "$url" 2>/dev/null || echo '?')"; else latest="$(latest_npm "$url" 2>/dev/null || echo '?')"; fi
        latest="${latest:-?}"
        local cur; cur="$(installed_version "$name")"
        local mark=''
        [[ "$cur" != "(not installed)" && "$cur" != "$latest" && "$latest" != "?" ]] && mark=' <-- new version available'
        printf '%-14s %-12s %s%s\n' "$name" "${cur:0:10}" "${latest:0:10}" "$mark"
    done
}

cmd="${1:-help}"
case "$cmd" in
    status)
        say 'Installed versions:'
        for entry in "${LIBS[@]}"; do
            IFS='|' read -r name _ <<< "$entry"
            printf '  %-14s %s\n' "$name" "$(installed_version "$name")"
        done
        ;;
    check)
        say 'Checking upstream for new versions ...'
        show_status
        ;;
    update)
        if [[ -n "${2:-}" ]]; then
            update_lib "$2" "${3:-}"
        else
            for entry in "${LIBS[@]}"; do
                IFS='|' read -r name _ <<< "$entry"
                update_lib "$name"
            done
        fi
        ;;
    *)
        sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'
        ;;
esac
