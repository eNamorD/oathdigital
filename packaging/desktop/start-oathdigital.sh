#!/bin/sh
# Double-click to start Oath Digital. Close this window or press Ctrl-C to stop.
cd "$(dirname "$0")" || exit 1
OATH_LAUNCH=desktop
export OATH_LAUNCH
bin/oathdigital "$@"
status=$?
case "$status" in
  0|130|143) ;;
  *)
    printf '\nOath Digital stopped with an error (exit %s). Press Return to close this window.\n' "$status"
    read -r _
    ;;
esac
exit "$status"
