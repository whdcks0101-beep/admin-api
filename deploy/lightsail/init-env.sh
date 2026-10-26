#!/bin/bash
set -eu
cd "$(dirname "$0")"
if [ -e .env ]; then
  echo '.env already exists; keeping existing credentials.'
  exit 0
fi
umask 077
# noclobber prevents accidental replacement even if another process creates it.
set -C
{
  printf 'PUBLIC_HOST=13-124-81-142.sslip.io\n'
  printf 'BACKEND_IMAGE=admin-api:cloud-1\nFRONTEND_IMAGE=admin-frontend:cloud-1\n'
  for name in DB_PASSWORD MYSQL_ROOT_PASSWORD KEYCLOAK_DB_PASSWORD KC_BOOTSTRAP_ADMIN_PASSWORD KEYCLOAK_CLIENT_SECRET; do
    printf '%s=%s\n' "$name" "$(openssl rand -hex 32)"
  done
  printf 'KAFKA_CLUSTER_ID=%s\n' "$(openssl rand -base64 16 | tr '+/' '-_' | tr -d '=\n')"
} > .env
echo '.env created with permissions 600. Secret values are not printed.'
