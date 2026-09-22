#!/bin/sh
# Smokes an extracted bundled-runtime archive under the desktop profile with
# no JAVA_HOME and no Java on PATH. macOS and Linux only.

set -eu

app_directory=${1:-}
port=${2:-}
log_file=
child_pid=
temporary=

fail() {
  printf 'bundled distribution smoke failed: %s\n' "$1" >&2
  if [ -n "$log_file" ] && [ -f "$log_file" ]; then
    sed 's#/s/[A-Za-z0-9_-][A-Za-z0-9_-]*#/s/[REDACTED]#g' "$log_file" >&2
  fi
  exit 1
}

cleanup() {
  if [ -n "$child_pid" ] && kill -0 "$child_pid" 2>/dev/null; then
    kill -KILL "$child_pid" 2>/dev/null || true
  fi
  [ -z "$temporary" ] || rm -rf -- "$temporary"
}
trap cleanup EXIT

case "$port" in ''|*[!0-9]*) fail "port must be an integer: $port" ;; esac
[ -x "$app_directory/jre/bin/java" ] || fail "missing bundled jre/ in $app_directory"
[ -x "$app_directory/Start Oath Digital.command" ] && [ -x "$app_directory/start-oathdigital.sh" ] ||
  fail "Start files must be present and executable"
app=$(cd "$app_directory" && pwd -P)
temporary=$(mktemp -d "${TMPDIR:-/tmp}/oathdigital-bundled.XXXXXX")
temporary=$(cd "$temporary" && pwd -P)
log_file="$temporary/server.log"
mkdir -p "$temporary/home"
case "$(uname -s)" in
  Darwin) app_data="$temporary/home/Library/Application Support/OathDigital" ;;
  *) app_data="$temporary/xdg/oathdigital" ;;
esac

env -i PATH=/usr/bin:/bin HOME="$temporary/home" XDG_DATA_HOME="$temporary/xdg" \
  OATH_LAUNCH=desktop OATH_OPEN_BROWSER=false OATH_PORT="$port" \
  "$app/bin/oathdigital" >"$log_file" 2>&1 &
child_pid=$!

url=
deadline=$(($(date +%s) + 90))
while [ -z "$url" ] && [ "$(date +%s)" -lt "$deadline" ]; do
  kill -0 "$child_pid" 2>/dev/null || fail "server exited before the banner"
  url=$(sed -n \
    -e 's/^  Players open:  \(http:[^ ]*\)$/\1/p' \
    -e 's/^  Only this computer can connect: \(http:[^ ]*\)$/\1/p' \
    "$log_file" | head -n 1)
  [ -n "$url" ] || sleep 1
done
[ -n "$url" ] || fail "banner did not appear within 90 seconds"
case "$url" in *":$port") ;; *) fail "banner address $url does not use port $port" ;; esac

for path in /health/ready / /assets/main.js; do
  curl -fsS --connect-timeout 2 --max-time 10 -o /dev/null "$url$path" ||
    fail "$url$path did not respond successfully"
done
ps -o command= -p "$child_pid" | grep -F "$app/jre/bin/java" >/dev/null ||
  fail "server is not running on the bundled runtime"
[ -f "$app_data/oathdigital.properties" ] || fail "settings file was not created"
ls "$app_data/data" 2>/dev/null | grep -q '^database\.' ||
  fail "database was not created in the app-data folder"

kill -TERM "$child_pid"
wait "$child_pid" 2>/dev/null || true
child_pid=
grep -q 'Oath Digital database closed' "$log_file" || fail "database did not close cleanly"
echo "bundled distribution smoke passed: $app"
