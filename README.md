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
Running fine on Php 8.4 and Symfony 7.2.

1. cp .env .env.local // fill .env.local with DB_PASS DATABASE_URL and *_MAP_API_KEY
2. //optional, if run via docker
   sed -n 's/DB_PASS=//p' .env.local > var/db_password.txt
   mkdir mysql-data; docker compose up -d
3. php composer.phar install //if clone from git, after install composer
4. php bin/console doctrine:database:create
5. php bin/console doctrine:schema:update --complete --force
6. php bin/console doctrine:fixtures:load //default user 'admin:admin'
   (use --append to reserve data on update)
7. mysql -uroot -p < wlwdw-backup.sql //optional, import database backup

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
