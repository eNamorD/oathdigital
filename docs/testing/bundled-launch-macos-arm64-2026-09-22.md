# Bundled launch checkpoint: macOS arm64, 2026-09-22

**Commit:** `3e77e65`
**Archive:** `oathdigital-0.1.0-SNAPSHOT-macos-arm64.tgz`
**Archive SHA-256:** `414f24ebb65c1d322fd53c2c4fbf66d4091782f956b28dca81d10d2dfabe51b2`
**macOS:** 26.6.2 (build 25G83), Apple silicon

## What was tested

The archive was placed in `~/Downloads` and marked with `com.apple.quarantine`
(`xattr -w`) to reproduce a real browser download, then extracted and started
by double-clicking **Start Oath Digital.command**, exactly as a host would.

## First attempt: found a real gap

Before the fix in `3e77e65`, this exact flow failed. Gatekeeper's approval of
the double-clicked Start file did not extend to `bin/oathdigital`, the
launcher script it calls: the server never started, failing with
`bash: bin/oathdigital: Operation not permitted` (exit 126). The plan had only
anticipated the bundled `java` binary needing this treatment, not the launcher
script itself. Fixed by having the Start file strip `com.apple.quarantine`
from its own sibling files on first run, since running it at all already
proves the user approved this exact download.

## Second attempt, after the fix: passed

1. Double-clicking the archive extracted it via Archive Utility with no prompt.
2. Double-clicking **Start Oath Digital.command** showed the standard
   Gatekeeper prompt: *"Apple could not verify... is free of malware..."*
   with **Move to Trash** / **Cancel**. Resolved via **System Settings ›
   Privacy & Security › Open Anyway**, then a second confirm in the same
   pane.
3. On relaunch, a system prompt appeared: *"Terminal.app would like to access
   files in your Downloads folder"* (Allow / Don't Allow). This is a macOS
   file-access (TCC) prompt, separate from Gatekeeper, and appears because the
   archive was run from inside Downloads. Not previously documented in the
   quick start; added there in this commit.
4. After **Allow**, the server started on the bundled runtime with no further
   prompts: a Terminal window showed the banner (`Oath Digital ... is
   running.`, `Players open: http://192.168.1.193:8080`, data folder and
   settings file paths, the seat-link-changes warning, and the close
   instruction), and the default browser opened automatically to the
   game-creation page.

## Not recorded here

Seat links, cookies, and raw server logs are excluded, consistent with the
project's evidence-handling rule. Game creation and a second machine joining
via a seat link (acceptance rows 21-22) were not exercised in this checkpoint
and remain UNEXECUTED, along with the Windows and Linux archives.
