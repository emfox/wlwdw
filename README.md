# Wlwdw: device location recoder and viewer

Running fine on Php 8.4 and Symfony 7.2.

## Copyright

GPL v2+

## Install

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
