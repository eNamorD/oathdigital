#!/bin/sh

set -eu

stage_directory=${1:-}
port=${2:-}
temporary_directory=
child_pid=
log_file=
log_files=
run_label=
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

redact_seat_links() {
  sed 's#/s/[A-Za-z0-9_-][A-Za-z0-9_-]*#/s/[REDACTED]#g'
}

fail() {
  printf 'packaged distribution smoke failed: %s\n' "$1" |
    redact_seat_links >&2
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

  if [ "$smoke_succeeded" -ne 1 ] && [ -n "$log_files" ]; then
    for cleanup_log in $log_files; do
      if [ -f "$cleanup_log" ]; then
        echo "--- packaged server log: $cleanup_log ---" >&2
        sed -n '1,240p' "$cleanup_log" | redact_seat_links >&2
      fi
    done
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
mkdir "$package_directory" "$database_directory"
cp -R "$stage_directory"/. "$package_directory"/

base_url=http://127.0.0.1:$port
catalog_path=$package_directory/share/oathdigital/new-foundations-component-catalog.json
database_path=$database_directory/oathdigital
index_file=$temporary_directory/index.html
game_path=/games/alpha%3Aone
creation_response=$temporary_directory/creation.json
command_response=$temporary_directory/command.json
exchange_error=$temporary_directory/exchange-error.log
red_cookie_jar=$temporary_directory/red.cookies
blue_cookie_jar=$temporary_directory/blue.cookies
yellow_cookie_jar=$temporary_directory/yellow.cookies
red_projection=$temporary_directory/red.json
blue_projection=$temporary_directory/blue.json
yellow_projection=$temporary_directory/yellow.json

cd "$package_directory"

# Each run gets its own log so the database-close assertion below cannot be
# satisfied by an earlier run's shutdown.
start_server() {
  run_label=$1
  log_file=$temporary_directory/server-$run_label.log
  log_files="$log_files $log_file"
  env \
    OATH_MODE=trusted-alpha \
    OATH_HOST=127.0.0.1 \
    OATH_PORT="$port" \
    OATH_PUBLIC_BASE_URL="$base_url" \
    OATH_DATABASE_PATH="$database_path" \
    OATH_CATALOG_PATH="$catalog_path" \
    ./bin/oathdigital >"$log_file" 2>&1 &
  child_pid=$!
}

wait_for_readiness() {
  readiness_deadline=$(($(date +%s) + 30))
  while :; do
    readiness_remaining=$(($readiness_deadline - $(date +%s)))
    if [ "$readiness_remaining" -le 0 ]; then
      fail "readiness timed out after 30 seconds on run $run_label"
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
      fail "server exited before readiness on run $run_label"
    fi
    if [ "$(date +%s)" -ge "$readiness_deadline" ]; then
      fail "readiness timed out after 30 seconds on run $run_label"
    fi
    if [ "$readiness_remaining" -gt 1 ]; then
      sleep 1
    fi
  done
}

check_frontend() {
  curl --fail --silent --show-error \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    "$base_url/" >"$index_file" ||
    fail "index request failed on run $run_label"
  grep -F '/assets/main.js' "$index_file" >/dev/null ||
    fail "index does not reference /assets/main.js on run $run_label"

  asset_status=$(curl --fail --silent --show-error --output /dev/null \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    --write-out '%{http_code}' "$base_url/assets/main.js") ||
    fail "asset request failed on run $run_label"
  [ "$asset_status" = 200 ] ||
    fail "asset returned HTTP $asset_status instead of 200 on run $run_label"
}

create_game() {
  creation_status=$(curl --silent --show-error \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    --output "$creation_response" \
    --write-out '%{http_code}' \
    --header 'Content-Type: application/json' \
    --data '{"gameId":"alpha:one","participants":[{"playerId":"red-exile","lineageId":"red-lineage","color":"red"},{"playerId":"blue-exile","lineageId":"blue-lineage","color":"blue"},{"playerId":"yellow-exile","lineageId":"yellow-lineage","color":"yellow"}],"firstPlayerId":"blue-exile"}' \
    "$base_url/games") ||
    fail "game creation request failed on run $run_label"
  [ "$creation_status" = 201 ] ||
    fail "game creation returned HTTP $creation_status instead of 201 on run $run_label"
  grep -F '"gameId":"alpha:one"' "$creation_response" >/dev/null ||
    fail "game creation response has the wrong game on run $run_label"
}

extract_seat_url() {
  player_id=$1
  seat_url=$(sed -n \
    "s#.*\"playerId\":\"$player_id\",\"url\":\"\\([^\"]*\\)\".*#\\1#p" \
    "$creation_response")
  [ -n "$seat_url" ] || fail "creation response omitted $player_id seat"
  case "$seat_url" in
    "$base_url"/s/*) ;;
    *) fail "creation response returned an invalid $player_id seat link" ;;
  esac
  printf '%s' "$seat_url"
}

exchange_seat() {
  player_id=$1
  seat_url=$2
  cookie_jar=$3
  if ! exchange_status=$(curl --silent --show-error \
      --connect-timeout "$curl_connect_timeout" \
      --max-time "$curl_request_timeout" \
      --output /dev/null \
      --write-out '%{http_code}' \
      --cookie-jar "$cookie_jar" \
      "$seat_url" 2>"$exchange_error"); then
    redact_seat_links <"$exchange_error" >&2
    fail "$player_id seat exchange request failed on run $run_label"
  fi
  [ "$exchange_status" = 303 ] ||
    fail "$player_id seat exchange returned HTTP $exchange_status instead of 303 on run $run_label"
  grep -F 'oath_seat' "$cookie_jar" >/dev/null ||
    fail "$player_id seat exchange did not store a cookie on run $run_label"
}

load_seat() {
  player_id=$1
  cookie_jar=$2
  projection_file=$3
  expected_sequence=$4
  page_file=$temporary_directory/page-$player_id-$run_label.html
  page_status=$(curl --silent --show-error \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    --output "$page_file" \
    --write-out '%{http_code}' \
    --cookie "$cookie_jar" \
    "$base_url$game_path") ||
    fail "$player_id canonical page request failed on run $run_label"
  [ "$page_status" = 200 ] ||
    fail "$player_id canonical page returned HTTP $page_status instead of 200 on run $run_label"
  grep -F '/assets/main.js' "$page_file" >/dev/null ||
    fail "$player_id canonical page omitted /assets/main.js on run $run_label"

  api_status=$(curl --silent --show-error \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    --output "$projection_file" \
    --write-out '%{http_code}' \
    --cookie "$cookie_jar" \
    "$base_url$game_path/api") ||
    fail "$player_id private API request failed on run $run_label"
  [ "$api_status" = 200 ] ||
    fail "$player_id private API returned HTTP $api_status instead of 200 on run $run_label"
  grep -F "\"viewerPlayerId\":\"$player_id\"" "$projection_file" >/dev/null ||
    fail "$player_id private API resolved the wrong seat on run $run_label"
  grep -F "\"nextSequence\":$expected_sequence" "$projection_file" >/dev/null ||
    fail "$player_id private API returned the wrong sequence on run $run_label"
}

submit_representative_command() {
  site_id=$(sed -n 's/.*"siteId":"\([^"]*\)".*/\1/p' "$blue_projection")
  [ -n "$site_id" ] || fail "blue-exile projection omitted a site ID"
  case "$site_id" in
    *[!A-Za-z0-9._:-]*) fail "blue-exile projection returned an invalid site ID" ;;
  esac
  command_status=$(curl --silent --show-error \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    --output "$command_response" \
    --write-out '%{http_code}' \
    --cookie "$blue_cookie_jar" \
    --header 'Content-Type: application/json' \
    --data "{\"expectedNextSequence\":1,\"intent\":{\"type\":\"placePawn\",\"siteId\":\"$site_id\"}}" \
    "$base_url$game_path/api/commands") ||
    fail "representative command request failed on run $run_label"
  [ "$command_status" = 200 ] ||
    fail "representative command returned HTTP $command_status instead of 200 on run $run_label"
  grep -F '"viewerPlayerId":"blue-exile"' "$command_response" >/dev/null ||
    fail "representative command resolved the wrong seat on run $run_label"
  grep -F '"nextSequence":2' "$command_response" >/dev/null ||
    fail "representative command did not advance the game on run $run_label"
}

stop_server() {
  kill -TERM "$child_pid" 2>/dev/null ||
    fail "could not send TERM to packaged server on run $run_label"
  shutdown_deadline=$(($(date +%s) + 15))
  while child_is_running && [ "$(date +%s)" -lt "$shutdown_deadline" ]; do
    sleep 1
  done
  if child_is_running; then
    fail "server did not stop within 15 seconds on run $run_label"
  fi

  set +e
  wait "$child_pid"
  child_exit=$?
  set -e
  child_pid=
  case "$child_exit" in
    0|143) ;;
    *)
      fail "server exited with unexpected status $child_exit after TERM on run $run_label"
      ;;
  esac

  grep -F 'Oath Digital database closed' "$log_file" >/dev/null ||
    fail "server did not log database close evidence on run $run_label"
}

start_server 1
wait_for_readiness
check_frontend
create_game
red_seat_url=$(extract_seat_url red-exile)
blue_seat_url=$(extract_seat_url blue-exile)
yellow_seat_url=$(extract_seat_url yellow-exile)
exchange_seat red-exile "$red_seat_url" "$red_cookie_jar"
exchange_seat blue-exile "$blue_seat_url" "$blue_cookie_jar"
exchange_seat yellow-exile "$yellow_seat_url" "$yellow_cookie_jar"
load_seat red-exile "$red_cookie_jar" "$red_projection" 1
load_seat blue-exile "$blue_cookie_jar" "$blue_projection" 1
load_seat yellow-exile "$yellow_cookie_jar" "$yellow_projection" 1
submit_representative_command
stop_server

[ -f "$database_path.properties" ] ||
  fail "HSQLDB properties file was not created"
[ -f "$database_path.script" ] ||
  fail "HSQLDB script file was not created"

# Reopen the same database directory in a second process. A corrupt,
# half-written, or lock-stranded HSQLDB database fails this startup, which is
# what "persistence" has to mean beyond the files merely existing.
start_server 2
wait_for_readiness
check_frontend
load_seat red-exile "$red_cookie_jar" "$red_projection" 2
load_seat blue-exile "$blue_cookie_jar" "$blue_projection" 2
load_seat yellow-exile "$yellow_cookie_jar" "$yellow_projection" 2
exchange_seat red-exile "$red_seat_url" "$red_cookie_jar"
exchange_seat blue-exile "$blue_seat_url" "$blue_cookie_jar"
exchange_seat yellow-exile "$yellow_seat_url" "$yellow_cookie_jar"
stop_server

[ -f "$database_path.properties" ] ||
  fail "HSQLDB properties file did not survive the second run"
[ -f "$database_path.script" ] ||
  fail "HSQLDB script file did not survive the second run"

smoke_succeeded=1
echo "packaged distribution smoke passed: readiness, frontend, three private seats, command, seat restoration across restart, database close, shutdown"
