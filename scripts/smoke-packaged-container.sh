#!/bin/sh

set -eu

image=${1:-}
port=${2:-}
container_name=
volume_name=
container_created=0
volume_created=0
smoke_succeeded=0

if [ -z "$image" ]; then
  echo "packaged container image is required" >&2
  exit 1
fi
case "$port" in
  ''|*[!0-9]*)
    echo "packaged container smoke port must be an integer: $port" >&2
    exit 1
    ;;
esac
if [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
  echo "packaged container smoke port out of range: $port" >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for packaged container smoke testing" >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is unavailable for packaged container smoke testing" >&2
  exit 1
fi
if ! docker image inspect "$image" >/dev/null 2>&1; then
  echo "packaged container image not found: $image" >&2
  exit 1
fi

fail() {
  echo "packaged container smoke failed: $1" >&2
  exit 1
}

cleanup() {
  if [ "$smoke_succeeded" -ne 1 ] && [ "$container_created" -eq 1 ] &&
      [ -n "$container_name" ]; then
    echo "--- packaged container log ---" >&2
    docker logs "$container_name" 2>&1 | sed -n '1,240p' >&2 || true
  fi
  if [ "$container_created" -eq 1 ] && [ -n "$container_name" ]; then
    docker rm --force "$container_name" >/dev/null 2>&1 || true
  fi
  container_created=0
  if [ "$volume_created" -eq 1 ] && [ -n "$volume_name" ]; then
    docker volume rm "$volume_name" >/dev/null 2>&1 || true
  fi
  volume_created=0
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

unique_suffix=$(date +%s)-$$
container_name=oathdigital-smoke-container-$unique_suffix
volume_name=oathdigital-smoke-volume-$unique_suffix

if docker container inspect "$container_name" >/dev/null 2>&1; then
  fail "unique container name already exists: $container_name"
fi
if docker volume inspect "$volume_name" >/dev/null 2>&1; then
  fail "unique volume name already exists: $volume_name"
fi

docker volume create "$volume_name" >/dev/null
volume_created=1

docker create \
  --name "$container_name" \
  --publish "127.0.0.1:$port:8080" \
  --env OATH_MODE=trusted-alpha \
  --env OATH_HOST=0.0.0.0 \
  --env OATH_PORT=8080 \
  --env OATH_PUBLIC_BASE_URL="http://127.0.0.1:$port" \
  --env OATH_DATABASE_PATH=/var/lib/oathdigital/database \
  --volume "$volume_name:/var/lib/oathdigital" \
  "$image" >/dev/null
container_created=1
docker start "$container_name" >/dev/null

base_url=http://127.0.0.1:$port

wait_for_readiness() {
  readiness_deadline=$(($(date +%s) + 30))
  while ! curl --fail --silent --show-error \
      "$base_url/health/ready" >/dev/null 2>&1; do
    container_running=$(docker inspect --format '{{.State.Running}}' \
      "$container_name" 2>/dev/null || true)
    [ "$container_running" = true ] ||
      fail "container exited before readiness"
    if [ "$(date +%s)" -ge "$readiness_deadline" ]; then
      fail "readiness timed out after 30 seconds"
    fi
    sleep 1
  done
}

check_frontend() {
  index_body=$(curl --fail --silent --show-error "$base_url/") ||
    fail "index request failed"
  printf '%s' "$index_body" | grep -F '/assets/main.js' >/dev/null ||
    fail "index does not reference /assets/main.js"

  asset_status=$(curl --fail --silent --show-error --output /dev/null \
    --write-out '%{http_code}' "$base_url/assets/main.js") ||
    fail "asset request failed"
  [ "$asset_status" = 200 ] ||
    fail "asset returned HTTP $asset_status instead of 200"
}

wait_for_readiness
check_frontend
docker restart "$container_name" >/dev/null
wait_for_readiness
check_frontend
docker stop --time 15 "$container_name" >/dev/null

container_exit=$(docker inspect --format '{{.State.ExitCode}}' "$container_name")
case "$container_exit" in
  0|143) ;;
  *) fail "container exited with unexpected status $container_exit after stop" ;;
esac

smoke_succeeded=1
echo "packaged container smoke passed: readiness, index, asset, restart, shutdown"
