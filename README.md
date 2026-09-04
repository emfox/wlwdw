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

1. Generate a local config `.env.local` from the `.env` template with random
   secrets filled in automatically. The file is gitignored so it never gets
   committed. (Skip if `.env.local` already exists; re-running overwrites the
   passwords.)
```bash
cp .env .env.local \
  && APP_SECRET=$(php -r 'echo bin2hex(random_bytes(32));') \
  && DB_PASS=$(php -r 'echo bin2hex(random_bytes(16));') \
  && sed -i.bak "s/^APP_SECRET=.*/APP_SECRET=$APP_SECRET/; s/^DB_PASS=.*/DB_PASS=$DB_PASS/" .env.local \
  && rm -f .env.local.bak
```
   `DATABASE_URL` in `.env` already references `${DB_PASS}`, so it follows the
   generated value automatically. Finally edit `.env.local` and replace the
   `*_MAP_API_KEY` placeholders with the keys you obtained for Google Maps /
   Baidu Maps.

2. Optional, if running via docker: write the generated DB password into the
   docker secret file, then bring the stack up.
```bash
sed -n 's/^DB_PASS=//p' .env.local > var/db_password.txt
mkdir -p mysql-data && docker compose up -d
```

3. `php composer.phar install` //if clone from git, after install composer
4. `php bin/console doctrine:database:create`
5. `php bin/console doctrine:schema:update --complete --force`
6. `php bin/console doctrine:fixtures:load` //default user 'admin:admin'
   (use --append to reserve data on update). Remember to change the admin
   password afterwards from the user admin page, or edit the fixture first.
7. `mysql -uroot -p < wlwdw-backup.sql` //optional, import database backup

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
