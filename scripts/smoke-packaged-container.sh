#!/bin/sh

set -eu

image=${1:-}
port=${2:-}
container_name=
volume_name=
container_created=0
volume_created=0
temporary_directory=
run_label=
smoke_succeeded=0
curl_connect_timeout=2
curl_request_timeout=10

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

redact_seat_links() {
  sed 's#/s/[A-Za-z0-9_-][A-Za-z0-9_-]*#/s/[REDACTED]#g'
}

fail() {
  printf 'packaged container smoke failed: %s\n' "$1" |
    redact_seat_links >&2
  exit 1
}

cleanup() {
  if [ "$smoke_succeeded" -ne 1 ] && [ "$container_created" -eq 1 ] &&
      [ -n "$container_name" ]; then
    echo "--- packaged container log ---" >&2
    docker logs "$container_name" 2>&1 |
      redact_seat_links | sed -n '1,240p' >&2 || true
  fi
  if [ "$container_created" -eq 1 ] && [ -n "$container_name" ]; then
    docker rm --force "$container_name" >/dev/null 2>&1 || true
  fi
  container_created=0
  if [ "$volume_created" -eq 1 ] && [ -n "$volume_name" ]; then
    docker volume rm "$volume_name" >/dev/null 2>&1 || true
  fi
  volume_created=0
  if [ -n "$temporary_directory" ] && [ -d "$temporary_directory" ]; then
    rm -rf -- "$temporary_directory"
  fi
  temporary_directory=
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

unique_suffix=$(date +%s)-$$
container_name=oathdigital-smoke-container-$unique_suffix
volume_name=oathdigital-smoke-volume-$unique_suffix
temporary_root=${TMPDIR:-/tmp}
temporary_directory=$(mktemp -d "$temporary_root/oathdigital-container-smoke.XXXXXX")
game_id=packaged-seat-smoke
creation_response=$temporary_directory/creation.json
command_response=$temporary_directory/command.json
exchange_error=$temporary_directory/exchange-error.log
red_cookie_jar=$temporary_directory/red.cookies
blue_cookie_jar=$temporary_directory/blue.cookies
yellow_cookie_jar=$temporary_directory/yellow.cookies
red_projection=$temporary_directory/red.json
blue_projection=$temporary_directory/blue.json
yellow_projection=$temporary_directory/yellow.json

if docker container inspect "$container_name" >/dev/null 2>&1; then
  fail "unique container name already exists: $container_name"
fi
if docker volume inspect "$volume_name" >/dev/null 2>&1; then
  fail "unique volume name already exists: $volume_name"
fi

docker volume create "$volume_name" >/dev/null
volume_created=1

# Only the overrides the loopback publish genuinely requires are passed here.
# OATH_HOST and OATH_DATABASE_PATH deliberately come from the image's own
# defaults, so this gate exercises what `docker run` does out of the box.
docker create \
  --name "$container_name" \
  --publish "127.0.0.1:$port:8080" \
  --env OATH_PORT=8080 \
  --env OATH_PUBLIC_BASE_URL="http://127.0.0.1:$port" \
  --volume "$volume_name:/var/lib/oathdigital" \
  "$image" >/dev/null
container_created=1
docker start "$container_name" >/dev/null

base_url=http://127.0.0.1:$port

wait_for_readiness() {
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
    container_running=$(docker inspect --format '{{.State.Running}}' \
      "$container_name" 2>/dev/null || true)
    [ "$container_running" = true ] ||
      fail "container exited before readiness"
    if [ "$(date +%s)" -ge "$readiness_deadline" ]; then
      fail "readiness timed out after 30 seconds"
    fi
    if [ "$readiness_remaining" -gt 1 ]; then
      sleep 1
    fi
  done
}

check_frontend() {
  index_body=$(curl --fail --silent --show-error \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    "$base_url/") ||
    fail "index request failed"
  printf '%s' "$index_body" | grep -F '/assets/main.js' >/dev/null ||
    fail "index does not reference /assets/main.js"

  asset_status=$(curl --fail --silent --show-error --output /dev/null \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    --write-out '%{http_code}' "$base_url/assets/main.js") ||
    fail "asset request failed"
  [ "$asset_status" = 200 ] ||
    fail "asset returned HTTP $asset_status instead of 200"
}

create_game() {
  creation_status=$(curl --silent --show-error \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    --output "$creation_response" \
    --write-out '%{http_code}' \
    --header 'Content-Type: application/json' \
    --data '{"gameId":"packaged-seat-smoke","participants":[{"playerId":"red-exile","lineageId":"red-lineage","color":"red"},{"playerId":"blue-exile","lineageId":"blue-lineage","color":"blue"},{"playerId":"yellow-exile","lineageId":"yellow-lineage","color":"yellow"}]}' \
    "$base_url/games") ||
    fail "game creation request failed on run $run_label"
  [ "$creation_status" = 201 ] ||
    fail "game creation returned HTTP $creation_status instead of 201 on run $run_label"
  grep -F '"gameId":"packaged-seat-smoke"' "$creation_response" >/dev/null ||
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
    "$base_url/games/$game_id") ||
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
    "$base_url/games/$game_id/api") ||
    fail "$player_id private API request failed on run $run_label"
  [ "$api_status" = 200 ] ||
    fail "$player_id private API returned HTTP $api_status instead of 200 on run $run_label"
  grep -F "\"viewerPlayerId\":\"$player_id\"" "$projection_file" >/dev/null ||
    fail "$player_id private API resolved the wrong seat on run $run_label"
  grep -F "\"nextSequence\":$expected_sequence" "$projection_file" >/dev/null ||
    fail "$player_id private API returned the wrong sequence on run $run_label"
}

submit_representative_command() {
  # Seating order and first player are shuffled per game (2026-09-22
  # generated-game randomization), so the active participant after
  # GameStarted's first walker park cannot be assumed to be any fixed seat.
  # Read it from the shared game-state field, present identically in every
  # viewer's own projection.
  active_player_id=$(sed -n 's/.*"activeParticipantId":"\([^"]*\)".*/\1/p' "$red_projection" | head -n 1)
  [ -n "$active_player_id" ] || fail "could not determine the active participant on run $run_label"
  case "$active_player_id" in
    red-exile) active_cookie_jar=$red_cookie_jar; active_projection=$red_projection ;;
    blue-exile) active_cookie_jar=$blue_cookie_jar; active_projection=$blue_projection ;;
    yellow-exile) active_cookie_jar=$yellow_cookie_jar; active_projection=$yellow_projection ;;
    *) fail "unrecognized active participant $active_player_id on run $run_label" ;;
  esac

  # The first setup decision (pawn placement) is a walker "choose-one" of a
  # site, exposed only in walkerDecision (the non-active seats instead carry
  # walkerWaiting). Scope extraction to text after the walkerDecision key so
  # a site ID from the world-board listing earlier in the same document is
  # never mistaken for one of this decision's actual options.
  decision_tail=$(sed -n 's/.*"walkerDecision"//p' "$active_projection")
  [ -n "$decision_tail" ] || fail "$active_player_id projection omitted a walker decision"
  decision_id=$(printf '%s' "$decision_tail" |
    sed -n 's/.*"decisionId":"\([^"]*\)".*/\1/p' | head -n 1)
  [ -n "$decision_id" ] || fail "$active_player_id walker decision omitted a decision ID"
  site_id=$(printf '%s' "$decision_tail" |
    grep -o '"id":"site:[^"]*"' | head -n 1 | sed 's/^"id":"//; s/"$//')
  [ -n "$site_id" ] || fail "$active_player_id walker decision offered no site option"
  case "$site_id" in
    *[!A-Za-z0-9._:-]*) fail "$active_player_id walker decision returned an invalid site ID" ;;
  esac

  command_status=$(curl --silent --show-error \
    --connect-timeout "$curl_connect_timeout" \
    --max-time "$curl_request_timeout" \
    --output "$command_response" \
    --write-out '%{http_code}' \
    --cookie "$active_cookie_jar" \
    --header 'Content-Type: application/json' \
    --data "{\"expectedNextSequence\":2,\"intent\":{\"type\":\"resolveWalker\",\"decisionId\":\"$decision_id\",\"payload\":{\"kind\":\"choose-one\",\"optionKind\":\"site\",\"optionId\":\"$site_id\"}}}" \
    "$base_url/games/$game_id/api/commands") ||
    fail "representative command request failed on run $run_label"
  [ "$command_status" = 200 ] ||
    fail "representative command returned HTTP $command_status instead of 200 on run $run_label"
  grep -F "\"viewerPlayerId\":\"$active_player_id\"" "$command_response" >/dev/null ||
    fail "representative command resolved the wrong seat on run $run_label"

  # How many automatic follow-up steps Setup takes after one placed pawn
  # varies with the randomized board, so only the exact pre-command value
  # (2) is fixed; a real advance just needs to be strictly greater than it.
  post_command_sequence=$(sed -n 's/.*"nextSequence":\([0-9]*\).*/\1/p' "$command_response" | head -n 1)
  [ -n "$post_command_sequence" ] || fail "representative command response omitted nextSequence on run $run_label"
  [ "$post_command_sequence" -gt 2 ] ||
    fail "representative command did not advance the game on run $run_label"
}

run_label=1
wait_for_readiness
check_frontend
create_game
red_seat_url=$(extract_seat_url red-exile)
blue_seat_url=$(extract_seat_url blue-exile)
yellow_seat_url=$(extract_seat_url yellow-exile)
exchange_seat red-exile "$red_seat_url" "$red_cookie_jar"
exchange_seat blue-exile "$blue_seat_url" "$blue_cookie_jar"
exchange_seat yellow-exile "$yellow_seat_url" "$yellow_cookie_jar"
load_seat red-exile "$red_cookie_jar" "$red_projection" 2
load_seat blue-exile "$blue_cookie_jar" "$blue_projection" 2
load_seat yellow-exile "$yellow_cookie_jar" "$yellow_projection" 2
submit_representative_command
docker restart --time 15 "$container_name" >/dev/null
run_label=2
wait_for_readiness
check_frontend
load_seat red-exile "$red_cookie_jar" "$red_projection" "$post_command_sequence"
load_seat blue-exile "$blue_cookie_jar" "$blue_projection" "$post_command_sequence"
load_seat yellow-exile "$yellow_cookie_jar" "$yellow_projection" "$post_command_sequence"
exchange_seat red-exile "$red_seat_url" "$red_cookie_jar"
exchange_seat blue-exile "$blue_seat_url" "$blue_cookie_jar"
exchange_seat yellow-exile "$yellow_seat_url" "$yellow_cookie_jar"

# `docker logs` concatenates every run of the container, so an unwindowed grep
# would be satisfied by the shutdown the restart above already performed. Bound
# the search to the final stop instead.
stop_started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker stop --time 15 "$container_name" >/dev/null

container_exit=$(docker inspect --format '{{.State.ExitCode}}' "$container_name")
case "$container_exit" in
  0|143) ;;
  *) fail "container exited with unexpected status $container_exit after stop" ;;
esac
docker logs --since "$stop_started" "$container_name" 2>&1 | \
  grep -F 'Oath Digital database closed' >/dev/null ||
  fail "container did not log database close evidence for the final stop"

smoke_succeeded=1
echo "packaged container smoke passed: readiness, frontend, three private seats, command, seat restoration across restart, database close, shutdown"
