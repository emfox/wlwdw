# Wlwdw
A device location recoder and viewer

## Copyright
Wlwdw: A device location recoder and viewer
Copyright (C) 2014-2025 Emfox Zhou (emfoxzhou@gmail.com)

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

## Install
Running fine on Php 8.4 and Symfony 8.1.

1. Generate a local config `.env` from the `.env.example` template with random
   secrets filled in automatically. The file is gitignored so it never gets
   committed. (Skip if `.env` already exists; re-running overwrites the
   passwords.)
```bash
cp .env.example .env \
  && APP_SECRET=$(php -r 'echo bin2hex(random_bytes(32));') \
  && DB_PASS=$(php -r 'echo bin2hex(random_bytes(16));') \
  && sed -i.bak "s/^APP_SECRET=.*/APP_SECRET=$APP_SECRET/; s/^DB_PASS=.*/DB_PASS=$DB_PASS/" .env \
  && rm -f .env.bak
```
   `DATABASE_URL` in `.env` already references `${DB_PASS}`, so it follows the
   generated value automatically. Finally edit `.env` and replace the
   `*_MAP_API_KEY` placeholders with the keys you obtained for Google Maps /
   Baidu Maps. If you use the realtime push feature (see "Realtime push (MQTT)"
   below), also fill `MQTT_SERVER_PASSWORD` and `MQTT_DEVICE_SHARED_SECRET` and
   the two EMQX keys (`EMQX_DASHBOARD__DEFAULT_PASSWORD`,
   `EMQX_API_KEY_SECRET`) with random hex — compose.yml reads them straight
   from `.env` for the emqx container.

2. Optional, if running via docker: bring the stack up. No secret-file
   derivation step: compose.yml takes `DB_PASS` as the MySQL secret and renders
   the EMQX credentials from `.env` natively.
```bash
mkdir -p mysql-data && docker compose up -d
```

3. `php composer.phar install` //if clone from git, after install composer
4. `php bin/console doctrine:database:create`
5. `php bin/console doctrine:schema:update --complete --force`
6. Create an admin user. This command works in every environment, including
   prod (the fixtures loader below only exists in dev/test):
```bash
php bin/console app:create-admin                        # prompts for a password
php bin/console app:create-admin --username admin --password 'S3cret!x'  # or pass it
```
   If no password is given on a non-interactive shell, a random one is
   generated and printed once. Re-running for an existing username refuses to
   overwrite it. (Dev/test only alternative: `php bin/console
   doctrine:fixtures:load` seeds the same default user 'admin' with password
   'admin' -- use --append to reserve data on update, and change the password
   afterwards from the user admin page.)
7. `mysql -uroot -p < wlwdw-backup.sql` //optional, import database backup

8. Optional: insert demo data. Re-running is safe,only adds or repairs `demo-*` data.
```bash
php bin/console app:seed-demo-data --apply # no --apply for dry run
```

## Realtime push (MQTT)

`/message/send` persists a message row per recipient (devid) and publishes a
realtime notification (`{"id": <row id>}`) to the device's MQTT topic so the
Android client can pull the body via `/message/{id}/{devid}`.

The broker is the **`emqx` service of this repo's `compose.yml`** (self-hosted
EMQX 5.8, same host, same docker project as web/php/mysql):

- Devices connect to `wss://mqtt.rpwt.org/mqtt` (443, via the shared external
  nginx-proxy). Credentials: username = device id (devid), password = the
  shared device secret (`MQTT_DEVICE_SHARED_SECRET`).
- This backend publishes to the broker over the project's default network as
  `emqx:1883` (service name), using the `mqtt-server` account
  (`MQTT_SERVER_USER`/`MQTT_SERVER_PASSWORD`).
- EMQX authenticates every CONNECT by calling back
  `http://web/mqtt/auth` (same default network; see `MqttAuthController`); the
  response carries a per-client ACL limiting a device to its own devid topic
  plus the broadcast topic `all`. Anything the ACL does not allow is denied by
  the broker-side `authorization.no_match=deny` (wired up by
  `bin/mqtt-provision.sh`).

The MQTT app settings live in `.env` under `### MQTT push`; `compose.yml`
overrides `MQTT_BROKER_HOST` for the php container. EMQX's own two secrets
(dashboard admin password + management API key) also live in that gitignored
`.env` and are handed to the emqx container natively by compose (environment +
a config) — no separate derived files.

### First-time EMQX setup (one-off, idempotent)

EMQX cannot express its HTTP-auth config via env vars, so it is provisioned
once over its REST API (config persists in `mqtt-data/`). Run after the app's
`/mqtt/auth` endpoint is confirmed live:

1. Ensure the EMQX credentials in `.env` (under `### MQTT push`) are real
   random hex values. Generate them with `openssl rand -hex 16`; compose.yml
   reads them straight from `.env` (the dashboard password becomes the emqx
   container env var, `EMQX_API_KEY_SECRET` is rendered into the emqx bootstrap
   key file as `dev_key:<secret>`).
   > The two keys involved are `EMQX_DASHBOARD__DEFAULT_PASSWORD` (dashboard
   > admin, loopback-only) and `EMQX_API_KEY_SECRET`. EMQX parses its env
   > values as HOCON, so the values must stay hex (no `#`/`:`/`=` characters).
2. Bring the stack up (EMQX imports its bootstrap API key only on a fresh
   `mqtt-data/`):
   ```bash
   docker compose up -d
   docker compose logs -f emqx     # "EMQX 5.x is running now" = OK; Ctrl-C to exit
   ```
   If a key did not get bootstrapped, reset: `docker compose down && rm -rf
   mqtt-data && docker compose up -d`.
3. Provision authn/authz (removes EMQX's built-in `{allow, all}` authorizer,
   adds an empty built_in_database authorizer, creates the HTTP authenticator
   to `/mqtt/auth`). The script reads `EMQX_API_KEY_SECRET` from `.env`:
   ```bash
   ./bin/mqtt-provision.sh
   ```
   Verify `authorization.no_match` is `deny`:
   `docker compose exec emqx emqx ctl conf show authorization`.

Rotation: change the two keys in `.env`, then rotate them **on a fresh
`mqtt-data/`** (dashboard/API-key changes only apply on first boot; afterwards
manage via the dashboard or the `/api/v5/api_key` API). To rotate the client
secrets, just change `MQTT_DEVICE_SHARED_SECRET`/`MQTT_SERVER_PASSWORD` in
`.env` (and the Android built-in secret) and restart the stack — EMQX only
calls back `/mqtt/auth`, so no EMQX-side change is needed.

## Troubleshooting

If enconter cache permisson problem, try clean cache:

php bin/console cache:clear --env=dev
chown -R www-data:www-data var/log
chown -R www-data:www-data var/cache
chmod 775 -R var/log
chmod 775 -R var/cache
chmod 644 var/log/.gitkeep
chmod 644 var/cache/.gitkeep

## Demo

https://wlwdw.rpwt.org
(not working all the time, online periodically)
