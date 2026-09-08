#!/bin/sh

set -eu

stage_directory=${1:-}
port=${2:-}
temporary_directory=
child_pid=
log_file=
smoke_succeeded=0
curl_connect_timeout=2
curl_request_timeout=10

if [ -z "$stage_directory" ] || [ ! -d "$stage_directory" ]; then
  echo "packaged stage directory not found: $stage_directory" >&2
  exit 1
fi

case "$port" in
  ''|*[!0-9]*)
    echo "packaged smoke port must be an integer: $port" >&2
    exit 1
    ;;
esac
if [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
  echo "packaged smoke port out of range: $port" >&2
  exit 1
fi

fail() {
  echo "packaged distribution smoke failed: $1" >&2
  exit 1
}

child_is_running() {
  [ -n "$child_pid" ] && kill -0 "$child_pid" 2>/dev/null
}

cleanup() {
  if child_is_running; then
    kill -TERM "$child_pid" 2>/dev/null || true
    cleanup_deadline=$(($(date +%s) + 15))
    while child_is_running && [ "$(date +%s)" -lt "$cleanup_deadline" ]; do
      sleep 1
    done
    if child_is_running; then
      kill -KILL "$child_pid" 2>/dev/null || true
    fi
    wait "$child_pid" 2>/dev/null || true
  fi
  child_pid=

  if [ "$smoke_succeeded" -ne 1 ] && [ -n "$log_file" ] &&
      [ -f "$log_file" ]; then
    echo "--- packaged server log ---" >&2
    sed -n '1,240p' "$log_file" >&2
  fi

  if [ -n "$temporary_directory" ] && [ -d "$temporary_directory" ]; then
    rm -rf -- "$temporary_directory"
  fi
  temporary_directory=
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

stage_directory=$(cd "$stage_directory" && pwd -P)
temporary_root=${TMPDIR:-/tmp}
temporary_directory=$(mktemp -d "$temporary_root/oathdigital-packaged-smoke.XXXXXX")
package_directory=$temporary_directory/package
database_directory=$temporary_directory/database
log_file=$temporary_directory/server.log
mkdir "$package_directory" "$database_directory"
cp -R "$stage_directory"/. "$package_directory"/

base_url=http://127.0.0.1:$port
catalog_path=$package_directory/share/oathdigital/new-foundations-component-catalog.json
database_path=$database_directory/oathdigital
index_file=$temporary_directory/index.html

cd "$package_directory"
env \
  OATH_MODE=trusted-alpha \
  OATH_HOST=127.0.0.1 \
  OATH_PORT="$port" \
  OATH_PUBLIC_BASE_URL="$base_url" \
  OATH_DATABASE_PATH="$database_path" \
  OATH_CATALOG_PATH="$catalog_path" \
  ./bin/oathdigital >"$log_file" 2>&1 &
child_pid=$!

readiness_deadline=$(($(date +%s) + 30))
while :; do
  readiness_remaining=$(($readiness_deadline - $(date +%s)))
  if [ "$readiness_remaining" -le 0 ]; then
    fail "readiness timed out after 30 seconds"
  fi
  readiness_connect_timeout=$curl_connect_timeout
  if [ "$readiness_remaining" -lt "$readiness_connect_timeout" ]; then
    readiness_connect_timeout=$readiness_remaining
  fi
  if curl --fail --silent --show-error \
      --connect-timeout "$readiness_connect_timeout" \
      --max-time "$readiness_remaining" \
      "$base_url/health/ready" >/dev/null 2>&1; then
    break
  fi
  if ! child_is_running; then
    fail "server exited before readiness"
  fi
  if [ "$(date +%s)" -ge "$readiness_deadline" ]; then
    fail "readiness timed out after 30 seconds"
  fi
  if [ "$readiness_remaining" -gt 1 ]; then
    sleep 1
  fi
done

curl --fail --silent --show-error \
  --connect-timeout "$curl_connect_timeout" \
  --max-time "$curl_request_timeout" \
  "$base_url/" >"$index_file" ||
  fail "index request failed"
grep -F '/assets/main.js' "$index_file" >/dev/null ||
  fail "index does not reference /assets/main.js"

asset_status=$(curl --fail --silent --show-error --output /dev/null \
  --connect-timeout "$curl_connect_timeout" \
  --max-time "$curl_request_timeout" \
  --write-out '%{http_code}' "$base_url/assets/main.js") ||
  fail "asset request failed"
[ "$asset_status" = 200 ] ||
  fail "asset returned HTTP $asset_status instead of 200"

kill -TERM "$child_pid" 2>/dev/null ||
  fail "could not send TERM to packaged server"
shutdown_deadline=$(($(date +%s) + 15))
while child_is_running && [ "$(date +%s)" -lt "$shutdown_deadline" ]; do
  sleep 1
done
if child_is_running; then
  fail "server did not stop within 15 seconds"
fi

set +e
wait "$child_pid"
child_exit=$?
set -e
child_pid=
case "$child_exit" in
  0|143) ;;
  *) fail "server exited with unexpected status $child_exit after TERM" ;;
esac

[ -f "$database_path.properties" ] ||
  fail "HSQLDB properties file was not created"
[ -f "$database_path.script" ] ||
  fail "HSQLDB script file was not created"
grep -F 'Oath Digital database closed' "$log_file" >/dev/null ||
  fail "server did not log database close evidence"

smoke_succeeded=1
echo "packaged distribution smoke passed: readiness, index, asset, persistence, shutdown"
