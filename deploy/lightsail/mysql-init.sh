#!/bin/bash
set -eu
# Generated as hex by init-env.sh; do not interpolate arbitrary SQL characters.
if [[ ! "${KEYCLOAK_DB_PASSWORD}" =~ ^[a-f0-9]{64}$ ]]; then
  echo 'KEYCLOAK_DB_PASSWORD must be a 64-character hex value' >&2
  exit 1
fi
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS keycloak CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'keycloak'@'%' IDENTIFIED BY '${KEYCLOAK_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON keycloak.* TO 'keycloak'@'%';
SQL
