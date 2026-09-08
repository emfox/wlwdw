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

1. Create `.env` from `.env.example`, filling the random secrets
   (`APP_SECRET`, `DB_PASS`, MQTT/EMQX keys) automatically. `.env` is
   gitignored; skip if it already exists (re-running overwrites the values):
```bash
cp .env.example .env \
  && APP_SECRET=$(php -r 'echo bin2hex(random_bytes(32));') \
  && DB_PASS=$(php -r 'echo bin2hex(random_bytes(16));') \
  && MQTT_SERVER_PASSWORD=$(php -r 'echo bin2hex(random_bytes(16));') \
  && EMQX_DASHBOARD__DEFAULT_PASSWORD=$(php -r 'echo bin2hex(random_bytes(16));') \
  && EMQX_API_KEY_SECRET=$(php -r 'echo bin2hex(random_bytes(16));') \
  && sed -i.bak -e "s/^APP_SECRET=.*/APP_SECRET=$APP_SECRET/" \
       -e "s/^DB_PASS=.*/DB_PASS=$DB_PASS/" \
       -e "s/^MQTT_SERVER_PASSWORD=.*/MQTT_SERVER_PASSWORD=$MQTT_SERVER_PASSWORD/" \
       -e "s/^EMQX_DASHBOARD__DEFAULT_PASSWORD=.*/EMQX_DASHBOARD__DEFAULT_PASSWORD=$EMQX_DASHBOARD__DEFAULT_PASSWORD/" \
       -e "s/^EMQX_API_KEY_SECRET=.*/EMQX_API_KEY_SECRET=$EMQX_API_KEY_SECRET/" .env \
  && rm -f .env.bak
```
   Then edit `.env` to set the map API keys (`GOOGLE_MAP_API_KEY`,
   `BAIDU_MAP_API_KEY`) and, if you use realtime push, `MQTT_DEVICE_SHARED_SECRET`
   to the same value the Android app embeds.

2. Optional, if running via docker (only `mysql-data` needs pre-creating; the
   EMQX data volume is created automatically):
```bash
mkdir -p mysql-data && docker compose up -d
```

3. `php composer.phar install`   // fresh clone only
4. `php bin/console doctrine:database:create`
5. `php bin/console doctrine:schema:update --complete --force`
6. Create an admin user (defaults to `admin`; prompts for a password, or
   generates one if run non-interactively). Refuses to overwrite an existing
   user. Dev/test only alternative: `doctrine:fixtures:load`.
```bash
php bin/console app:create-admin
php bin/console app:create-admin --username admin --password 'S3cret!x'  # non-interactive
```
7. Optional: import a database backup.
8. Optional: seed demo data (safe to re-run).
```bash
php bin/console app:seed-demo-data --apply    # no --apply for dry run
```

## Realtime push (MQTT)

`/message/send` persists a message row per recipient (devid) and publishes a
realtime notification (`{"id": <row id>}`) to the device's MQTT topic so the
Android client can pull the body via `/message/{id}/{devid}`.

The broker is the **`emqx` service of `compose.yml`** (self-hosted EMQX 5.8).
All MQTT settings live in `.env` under `### MQTT push`.

- Devices connect to `wss://wlwdw.rpwt.org/mqtt` (443, via the shared external
  nginx-proxy, which routes that domain's `/mqtt` path to the emqx service)
  as username = device id (devid), password = `MQTT_DEVICE_SHARED_SECRET`.
  The Android app resolves this host from its "custom server" setting
  (default: wlwdw.rpwt.org).
- The backend publishes to the broker as `emqx:1883` on the project's default
  network using `mqtt-server` / `MQTT_SERVER_PASSWORD`.
- On every CONNECT, EMQX calls back `http://web/mqtt/auth` (`MqttAuthController`)
  which returns the per-client ACL: a device may subscribe to `all` + its own
  devid and publish only to its devid; anything else is denied by
  `authorization.no_match=deny`.

### First-time setup

After `/mqtt/auth` is live, provision authn/authz once (idempotent):

```bash
docker compose up -d
./bin/mqtt-provision.sh
docker compose exec emqx emqx ctl conf show authorization   # no_match = deny
```

EMQX state (provision config, sessions) lives in the Docker volume
`wlwdw_mqtt_data` (auto-created). To re-provision from scratch (e.g. after
rotating the keys in `.env`):
```bash
docker compose down
docker volume rm wlwdw_mqtt_data
docker compose up -d
./bin/mqtt-provision.sh
```

> Moving the project to a new server does **not** move that volume with it.
> On the new host, `docker compose up -d` + `./bin/mqtt-provision.sh`
> recreates the config; queued offline messages are the only loss. Back it
> up explicitly if you want them too:
> ```bash
> docker run --rm -v wlwdw_mqtt_data:/data -v "$PWD":/backup alpine \
>   tar czf /backup/mqtt_data.tgz -C /data .
> ```

Rotating `MQTT_DEVICE_SHARED_SECRET` requires updating the Android app to
match.

## Troubleshooting

If you hit cache permission problems, clean the cache:

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
