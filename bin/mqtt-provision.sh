#!/usr/bin/env bash
# Provision EMQX (self-hosted wlwdw MQTT broker) over its REST API.
#
# EMQX env vars cannot express the HTTP-auth request body template and the
# file authorizer env path is buggy, so this script configures both over the
# management API (configuration persists in the mqtt_data volume's
# configs/cluster.hocon and survives restarts). It is idempotent: safe to
# re-run after redeploys.
#
# What it sets up:
#   1. Removes EMQX's BUILT-IN default file authorizer, whose final
#      {allow, all} rule would let every connected client do anything.
#   2. Adds an (empty) built_in_database authorizer so the broker-side
#      authorization chain exists; combined with authorization.no_match=deny
#      this denies anything the per-client ACL from /mqtt/auth did not allow.
#   3. Adds the password_based HTTP authenticator that calls the wlwdw app's
#      /mqtt/auth endpoint for every CONNECT. That response carries the
#      per-client ACL (allow subscribe "all"+own devid / publish own devid for
#      devices, allow all for mqtt-server).
#
# Usage:
#   ./bin/mqtt-provision.sh [--url http://web/mqtt/auth]
#
# The API key is "dev_key:<EMQX_API_KEY_SECRET>" (the name compose.yml's
# emqx_api_keys config bootstraps into EMQX on first boot); the secret is read
# from .env, or the whole "name:secret" can be passed via EMQX_API_KEY.
#
# The auth callback URL defaults to the wlwdw nginx service ("web") on this
# project's default network. The emqx and web containers share that network
# (both services of the same compose project), so EMQX reaches /mqtt/auth
# directly over internal HTTP (no TLS, no public round-trip, no cert
# verification). Override with --url only if you deliberately want a
# public/external endpoint instead.
set -euo pipefail

API_BASE="${EMQX_API_BASE:-http://127.0.0.1:18083/api/v5}"
AUTH_URL="${1:-http://web/mqtt/auth}"

# Resolve the API key: EMQX_API_KEY env var wins; otherwise read the secret
# from .env and prepend the "dev_key:" name that compose bootstraps.
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${EMQX_API_KEY:-}" ]]; then
    api_key="$EMQX_API_KEY"
else
    secret="$(sed -n 's/^EMQX_API_KEY_SECRET=//p' "$here/../.env" 2>/dev/null | head -n1 | tr -d '"')"
    if [[ -z "$secret" ]]; then
        echo "ERROR: EMQX_API_KEY_SECRET not found in .env" >&2
        exit 1
    fi
    api_key="dev_key:$secret"
fi

api() { # method path [json-body]
    local method="$1" path="$2" body="${3:-}"
    if [[ -n "$body" ]]; then
        curl -fsS -u "$api_key" -X "$method" -H 'Content-Type: application/json' \
            -H 'Accept: application/json' -d "$body" "$API_BASE$path" || return $?
    else
        curl -fsS -u "$api_key" -X "$method" -H 'Accept: application/json' \
            "$API_BASE$path" || return $?
    fi
}

echo "==> provisioning EMQX at $API_BASE"

# 1) Drop the default file authorizer ({allow, all} would defeat per-client ACLs).
if api DELETE /authorization/sources/file >/dev/null 2>&1; then
    echo "    removed default file authorizer"
else
    echo "    default file authorizer already absent"
fi

# 2) Ensure an empty built_in_database authorizer exists (the authorization
#    chain must exist for authorization.no_match=deny to take effect).
if ! api GET /authorization/sources >/dev/null 2>&1; then
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -u "$api_key" "$API_BASE/authorization/sources" 2>/dev/null || echo 'unreachable')"
    echo "ERROR: cannot reach the EMQX management API (HTTP $code). Common causes:" >&2
    echo "  - the container was started before this compose change: run 'docker compose up -d' again" >&2
    echo "    so the 127.0.0.1:18083 mapping exists (check 'docker compose ps')" >&2
    echo "  - EMQX is still booting (watch 'docker compose logs -f emqx')" >&2
    echo "  - the API key was not bootstrapped: EMQX imports its bootstrap key" >&2
    echo "    (rendered from EMQX_API_KEY_SECRET in .env) only on a fresh data" >&2
    echo "    dir, so if you started it before the key existed, run:" >&2
    echo "      docker compose down && docker volume rm wlwdw_mqtt_data && docker compose up -d" >&2
    exit 1
fi
if api POST /authorization/sources '{"type":"built_in_database","enable":true}' >/dev/null 2>&1; then
    echo "    added built_in_database authorizer (empty -> deny-all fallback)"
else
    echo "    built_in_database authorizer already present"
fi

# 3) HTTP password-based authentication -> wlwdw /mqtt/auth (internal HTTP).
#    EMQX allows one authenticator per id (password_based:http), so create it
#    only if absent. On failure abort with EMQX's error body so the cause
#    (e.g. an invalid_ssl_opts rejection) is visible.
existing_auth="$(api GET /authentication 2>/dev/null || true)"
if echo "$existing_auth" | grep -q '"backend": *"http"\|password_based'; then
    echo "    HTTP authenticator already present -> $AUTH_URL"
else
    payload="$(cat <<EOF
{
    "mechanism": "password_based",
    "backend": "http",
    "method": "post",
    "url": "$AUTH_URL",
    "body": {"username": "\${username}", "password": "\${password}", "clientid": "\${clientid}"}
}
EOF
)"
    # Do NOT use -f here: on failure we want EMQX's JSON error body on stderr
    # (e.g. an invalid_ssl_opts rejection) so the cause is visible, then exit.
    resp="$(curl -sS -u "$api_key" -X POST -H 'Content-Type: application/json' \
        -H 'Accept: application/json' -d "$payload" "$API_BASE/authentication")" \
        || { echo "ERROR: failed to create HTTP authenticator." >&2; \
             echo "$resp" | sed 's/^/    /' >&2; exit 1; }
    echo "    added HTTP authenticator -> $AUTH_URL"
fi

echo "==> done. Devices now authenticate via $AUTH_URL with the shared device secret."
