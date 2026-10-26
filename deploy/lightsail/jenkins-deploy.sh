#!/bin/bash
set -euo pipefail
service="${1:?service required}"
image="${2:?image required}"
: "${LIGHTSAIL_SSH_KEY:?Jenkins credential required}"
script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT
install -m 600 "$LIGHTSAIL_SSH_KEY" "$work_dir/key"
ssh_options=(-i "$work_dir/key" -o BatchMode=yes -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$script_dir/known_hosts"
  -o ConnectTimeout=15 -o ServerAliveInterval=30 -o ServerAliveCountMax=3)
case "$service:$image" in
  backend:admin-api:aws-*|frontend:admin-frontend:aws-*) ;;
  *) echo 'Unexpected service or image' >&2; exit 1 ;;
esac
[[ "$image" =~ ^[a-z-]+:aws-[0-9]+-[a-f0-9]{12}$ ]] || exit 1
docker image inspect "$image" >/dev/null
# pipefail makes a docker-save or SSH failure fail the Jenkins build.
docker save "$image" | ssh "${ssh_options[@]}" ubuntu@13.124.81.142 'sudo docker load'
ssh "${ssh_options[@]}" ubuntu@13.124.81.142 \
  "bash /home/ubuntu/admin-portfolio/apply-image.sh '$service' '$image'"
