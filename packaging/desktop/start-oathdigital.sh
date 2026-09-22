#!/bin/sh
# Double-click to start Oath Digital. Close this window or press Ctrl-C to stop.
cd "$(dirname "$0")" || exit 1
# macOS blocks each quarantined file the first time it runs, even after the
# user approves this Start file through Gatekeeper: approving this file does
# not cascade to bin/oathdigital or jre/bin/java. Running this file at all
# means the user already passed that approval for this exact download, so
# clear quarantine from the rest of the app tree now rather than making them
# repeat the same approval once per file. No-op on Linux and when xattr is
# absent; a failure here is not fatal, it just leaves the next command to
# fail with its own Gatekeeper message.
if [ "$(uname -s)" = "Darwin" ] && command -v xattr >/dev/null 2>&1; then
  xattr -dr com.apple.quarantine . 2>/dev/null || true
fi
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
