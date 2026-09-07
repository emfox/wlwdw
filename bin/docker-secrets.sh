#!/usr/bin/env bash
# Derive the project's gitignored local secret files (under var/) from the
# single source of truth, .env.local. Run before `docker compose up` so every
# container gets the credentials it needs.
#
# Scope: this script exists ONLY for the docker compose deployment. All three
# derived files feed containers (mysql secret, emqx env_file, emqx bootstrap
# key), and since compose defines emqx, missing any of them makes `docker
# compose config` fail. There is deliberately no partial/local mode: running
# the app outside docker does not use these files at all -- .env.local alone is
# enough there (see README Install step 1 vs step 2).
#
# Why a script instead of compose interpolation: docker compose ${} reads the
# tracked .env / shell only, never .env.local (the real-secret file), so
# container-bound secrets must be handed over as files under the gitignored
# var/ directory.
#
# Derived files (all idempotent, real values in .env.local are never
# overwritten; missing/placeholder keys get a random hex):
#   DB_PASS -> var/db_password     (MySQL docker secret)
#   EMQX_DASHBOARD__DEFAULT_PASSWORD -> var/mqtt.env  (emqx env_file)
#   EMQX_API_KEY_SECRET            -> var/emqx_api_keys (emqx bootstrap API key)
#
# Usage:
#   ./bin/docker-secrets.sh        # idempotent, safe to re-run
#
# .env.local and var/ are gitignored (real secrets only live on the server).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project="$(cd "$here/.." && pwd)"

ENV_LOCAL="$project/.env.local"
if [[ ! -f "$ENV_LOCAL" ]]; then
    echo "ERROR: $ENV_LOCAL not found." >&2
    echo "  Run the README Install step first (cp .env .env.local ...)." >&2
    exit 1
fi

random_hex() { openssl rand -hex 16; }

# get_or_set <varname>: echo the current value from .env.local; if absent or a
# placeholder, append/replace with a random hex and echo the new value.
get_or_set() {
    local name="$1" cur
    cur="$(sed -n "s/^${name}=//p" "$ENV_LOCAL" | head -n1)"
    if [[ -z "$cur" ]] || grep -qi 'change' <<<"$cur"; then
        cur="$(random_hex)"
        # Replace a placeholder line if one exists, otherwise append.
        if grep -q "^${name}=" "$ENV_LOCAL"; then
            sed -i "s|^${name}=.*|${name}=${cur}|" "$ENV_LOCAL"
            echo "    ${name}: replaced placeholder with a random value" >&2
        else
            printf '%s=%s\n' "$name" "$cur" >> "$ENV_LOCAL"
            echo "    ${name}: appended random value to .env.local" >&2
        fi
    fi
    printf '%s' "$cur"
}

echo "==> ensuring docker secrets in $(basename "$ENV_LOCAL")"
db_pass="$(get_or_set DB_PASS)"
dash_pass="$(get_or_set EMQX_DASHBOARD__DEFAULT_PASSWORD)"
api_secret="$(get_or_set EMQX_API_KEY_SECRET)"

mkdir -p "$project/var"

# 1) var/db_password: MySQL root password (compose secret db_password).
printf '%s\n' "$db_pass" > "$project/var/db_password"
echo "==> wrote $(basename "$project")/var/db_password (MySQL secret)"

# 2) var/mqtt.env: env_file for the emqx service (dashboard admin password
#    only). EMQX parses env values as HOCON, so '#', ':' or '=' inside the
#    password would corrupt it; generated hex values never contain them.
if [[ "$dash_pass" == *'#'* || "$dash_pass" == *':'* || "$dash_pass" == *'='* ]]; then
    echo "ERROR: EMQX_DASHBOARD__DEFAULT_PASSWORD in $ENV_LOCAL contains '#', ':' or '=' which" >&2
    echo "       EMQX cannot parse from an environment variable (HOCON). Use hex, e.g." >&2
    echo "       $(openssl rand -hex 16 2>/dev/null || echo 'openssl rand -hex 16')" >&2
    exit 1
fi
printf 'EMQX_DASHBOARD__DEFAULT_PASSWORD=%s\n' "$dash_pass" > "$project/var/mqtt.env"
echo "==> wrote $(basename "$project")/var/mqtt.env (env_file for the emqx service)"

# 3) var/emqx_api_keys: EMQX bootstrap file, a single 'name:secret' line, NO comment
#    lines (EMQX parses the first line as the key; a comment would abort the
#    import). The name stays "dev_key"; the secret comes from .env.local.
printf 'dev_key:%s\n' "$api_secret" > "$project/var/emqx_api_keys"
echo "==> wrote $(basename "$project")/var/emqx_api_keys (EMQX bootstrap API key)"

echo "==> done. Next: docker compose up -d, then ./bin/mqtt-provision.sh"
