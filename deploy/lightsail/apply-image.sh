#!/bin/bash
set -euo pipefail
cd /home/ubuntu/admin-portfolio
service="${1:?service required}"
image="${2:?image required}"
case "$service" in
  backend) variable=BACKEND_IMAGE; repository=admin-api ;;
  frontend) variable=FRONTEND_IMAGE; repository=admin-frontend ;;
  *) echo 'Unsupported service' >&2; exit 1 ;;
esac
if [[ ! "$image" =~ ^${repository}:aws-[0-9]+-[a-f0-9]{12}$ ]]; then
  echo 'Invalid image tag' >&2
  exit 1
fi
# Both Jenkins jobs share this server-side lock.
exec 9>.deploy.lock
flock -w 300 9
sudo docker image inspect "$image" >/dev/null
export "$variable=$image"
sudo --preserve-env="$variable" docker compose config --quiet
sudo --preserve-env="$variable" docker compose up -d --no-deps --pull never \
  --wait --wait-timeout 180 "$service"
# Persist only after health verification; preserve other service tags and secrets.
python3 - "$variable" "$image" <<'PY'
import os
import pathlib
import sys
import tempfile
key, value = sys.argv[1:]
path = pathlib.Path('.env')
lines = path.read_text().splitlines()
if sum(line.startswith(key + '=') for line in lines) != 1:
    raise SystemExit('Expected exactly one image setting in .env')
lines = [key + '=' + value if line.startswith(key + '=') else line for line in lines]
fd, name = tempfile.mkstemp(prefix='.env-deploy-', dir='.')
try:
    with os.fdopen(fd, 'w') as stream:
        stream.write('\n'.join(lines) + '\n')
    os.replace(name, path)
finally:
    if os.path.exists(name):
        os.unlink(name)
PY
sudo docker compose ps "$service"
echo "Deployment verified: $service -> $image"
