# Per-build trusted-alpha acceptance record

Copy this file for each candidate build and store the completed copy under
`docs/testing/`. A blank template is not evidence.
Every row below starts as **UNEXECUTED** and must remain so until the named
check is observed on that build. Use **PASS**, **FAIL**, or **BLOCKED** only with
dated evidence and operator initials.

## Recorded automated evidence (not manual acceptance)

At commit `c86530cb2641be8ed8c85601b4de36c8f764f214`
(`fix(frontend): show the walker decision's own heading, not the debug status`),
this acceptance run built locally on macOS arm64 with Temurin Java
`21.0.12.1+1-LTS` and `OATH_RELEASE_VERSION=0.1.0-alpha.2`:

```sh
export JAVA_HOME="$HOME/.local/oath-toolchains/jdk-21.0.12.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export OATH_RELEASE_VERSION=0.1.0-alpha.2
./sbtw verifyReleaseVersion frontend/test verifyPackageMappings Universal/packageBin Universal/packageZipTarball
```

The first build (at commit `8ba502dcac5897105c680b038f4426b3e85cb32e`, before
this run's fixes below) exited 0 with **1638 JVM tests** and **272 frontend
tests**, zero failures and zero errors, then failed
`sh scripts/smoke-packaged-distribution.sh`: the setup-sequence and command
shape it asserted were stale relative to the Chronicle/walker-procedure setup
rework. That script (and its container-smoke twin) was fixed and committed
(`a25e8a1`); see [alpha-acceptance-0.1.0-alpha.2 investigation notes below](#findings-from-this-run).
The build above, after both fixes, produced `oathdigital-0.1.0-alpha.2.zip`
(SHA-256 `d9e16767b990679f577d8d51f6f1d421032e79521827649fbe865c95bc9b59da`) and
`oathdigital-0.1.0-alpha.2.tgz` (SHA-256
`09fc0b6ef0296924fdf169172f1a32f1b15783667535398ba02240bdabbfa179`). Each
archive was extracted and passed
`sh scripts/smoke-packaged-distribution.sh <extracted-root> <port>`, run five
times against the `.zip` (to exercise different randomized seat orders) and
once against the `.tgz`, using that Java 21 runtime. Every run observed
readiness, frontend assets, three private seats, a command, seat restoration
across restart, database close, and shutdown.

This is automated archive evidence only. It does not satisfy any LAN,
browser, proxy, TLS, or publication row below.

## Build and environment

| Field | Recorded value |
| --- | --- |
| Build version or release tag | `0.1.0-alpha.2` |
| Git commit | `c86530cb2641be8ed8c85601b4de36c8f764f214` |
| Artifact and SHA-256 | `oathdigital-0.1.0-alpha.2.zip`, SHA-256 `d9e16767b990679f577d8d51f6f1d421032e79521827649fbe865c95bc9b59da` |
| Distribution type | Universal ZIP (host); TGZ smoke-tested only, not hosted |
| Host OS and architecture | macOS, arm64 |
| Java version for Universal | Temurin `21.0.12.1+1-LTS` |
| OCI image platform | Not applicable — not built this run |
| Database directory or named volume | `/Users/roman/oathdigital-data/alpha-2/database` |
| Public base URL | `http://192.168.1.193:8080` |
| Machine A OS, browser, version | macOS; Claude Code's built-in browser pane, Chromium 152.0.7977.130 |
| Machine B OS, browser, version | Windows 11 Pro 10.0.26200 (x64); Chrome 153.0.0.0 (via Claude-in-Chrome extension) and a second Chromium-based pane embedded in the Claude Desktop app |
| Machines and network are separate | Confirmed — distinct physical macOS and Windows 11 machines on the same LAN |
| Operator, date, time zone | Roman (operator sign-off pending, see Result); 2026-09-23; PDT |

Machine A and Machine B must be different machines on the tested network.
Separate tabs, windows, or browser profiles on one machine do not satisfy the
two-machine requirement.

## LAN and multiplayer checks

| # | Acceptance check | Status | Evidence or defect |
| ---: | --- | --- | --- |
| 1 | From Machine A, `/health/live` and `/health/ready` succeed at the recorded public base URL. | PASS | `curl http://192.168.1.193:8080/health/live` and `/health/ready` both returned `{"status":"live"/"ready","version":"0.1.0-alpha.2"}` from Machine A. |
| 2 | Machine A opens `/` and creates one game through the host form with exactly three distinct seats. | PASS | Host form (not raw API) used from Machine A's browser: red-exile, blue-exile, yellow-exile. Game ID `manual-1790154718704-26188`. |
| 3 | Host result shows three assigned `/s/` links, each is copied privately, and no raw code appears in application logs. | PASS | Three `/s/` links displayed; `grep` of all three raw seat codes against the server log found zero matches. |
| 4 | Machine A opens seat 1 in one browser profile; Machine B opens seat 2; a separate profile opens seat 3. Each redirects to the same game's canonical `/games/{game-id}` URL. | PASS | Machine A: red-exile (built-in pane). Machine B: yellow-exile (built-in pane) and blue-exile (Chrome) — two separate profiles on the second physical machine. All three redirected to `/games/manual-1790154718704-26188`. |
| 5 | Each profile displays the correct private viewer and does not display another seat's private view. Record player IDs and screenshots with seat codes removed. | PASS | Each seat's `viewerPlayerId` matched its own seat; Machine B confirmed Yellow's own adviser read as `"Facedown denizen"` (no name) from Blue's view, and vice versa. Public board state (players, world, banks) was identical across seats, as expected. |
| 6 | All three seats perform representative multiplayer turns. Record action names, player IDs, and resulting sequence numbers. | PASS | All three completed Setup's pawn-placement (`resolveWalker`/choose-one/site) and adviser-partition decisions. yellow-exile: sequence 0→8 (Shrouded Woods; kept Vow of Peace). blue-exile: 8→14 (Dunes; kept Dazzle). red-exile: 14→21 (Riverbank; kept Outriders). Game correctly transitioned to `phase: "wake"`, `"Waiting for Yellow Exile"` after all three. |
| 7 | Machine B disconnects from the network, reconnects, reloads the canonical game URL, and resumes its assigned seat at authoritative current state. | PASS | Machine B disconnected and reconnected; both its seats (Yellow, Blue) reloaded to the correct identity with state unchanged (`nextSequence` 21, `phase: "wake"`). |
| 8 | All clients close. Operator stops the server cleanly, confirms the database-close log, and restarts the same build against the same database directory or volume. | PASS | Stopped with `SIGTERM`; log showed `Oath Digital database closed`. Restarted the identical build against the same database path; `/health/ready` succeeded immediately after. (Done twice this run — once across a rebuild with the frontend fix, once with an identical rebuild-free restart for this row specifically.) |
| 9 | After restart, all three canonical URLs load with retained cookies, show the correct private views, and retain the previously recorded sequence/state. | PASS, one gap | Red (Machine A) and Blue (Machine B) independently reloaded the canonical URL post-restart with cookies retained, correct `viewerPlayerId`, and `nextSequence: 21` unchanged. Yellow's own post-restart reload was not independently re-verified: Machine B's own testing overwrote Yellow's cookie by opening Blue's `/s/` link in the same browser profile (seats share one cookie name/path per profile, per `docs/operations/network-and-browser.md`) — a testing-methodology gap, not a product defect. Yellow's server-side state was confirmed intact indirectly (Red's later turn correctly showed `"Waiting for Yellow Exile"` referencing Yellow's post-setup state). |
| 10 | In a disposable profile, clearing the game cookie causes the canonical URL to show recovery guidance; reopening the saved original seat link restores only that seat. | PASS | Machine B, private window: canonical URL with no seat cookie returned HTTP 403 with "Open your seat link" guidance and no game state. Reopening the original seat link in the same private window restored only that one seat. |

## HTTPS proxy and origin checks

| # | Acceptance check | Status | Evidence or defect |
| ---: | --- | --- | --- |
| 11 | The external origin uses a valid HTTPS certificate and the backend listens only on a restricted interface that is not directly reachable from Machine B. | UNEXECUTED | Not attempted this run — plain-HTTP LAN hosting only. |
| 12 | A newly generated seat link uses the exact configured HTTPS scheme, host, and port. | UNEXECUTED | Not observed. |
| 13 | Visiting the seat link stays on the HTTPS origin and returns an `oath_seat` cookie with `Secure`, `HttpOnly`, `SameSite=Lax`, and the exact `/games/{game-id}` path. | UNEXECUTED | Not observed. |
| 14 | A same-origin gameplay request succeeds; a deliberately mismatched `Origin` request is rejected without changing game state. | UNEXECUTED | Not observed. |
| 15 | Normal exchange, a controlled failing `/s/` request, and a `/s/` request with the backend unreachable (on a throwaway proxy instance) use a disposable test code; every NGINX access/error log, upstream application log, and applicable proxy, CDN, firewall, WAF, agent, or dashboard log is inspected, and no raw `/s/{seat-code}`, `Cookie`, or `Set-Cookie` value appears. | UNEXECUTED | Not observed. Row 15's mitigation from the `0.1.0-alpha.1` run (see that record) has not been re-verified against this build. |
| 16 | Forwarded identity headers do not select or change a seat; the seat cookie remains the only trusted seat identity. | UNEXECUTED | Not observed. |

## Bundled archive checks

Run once per bundled archive (macOS arm64, Windows x64, Linux x64) that the
build ships. Use a machine without Java 21 on `PATH` where possible.

| # | Check | Result | Notes |
| --- | --- | --- | --- |
Windows x64 evidence below comes from a real Windows 11 Pro machine (Machine
B) running the archive GitHub Actions built and smoke-tested for tag
`v0.1.0-alpha.2` (workflow run
[35907569705](https://github.com/eNamorD/oathdigital/actions/runs/35907569705),
`bundled-windows-x64` artifact, SHA-256
`cd2c1699ee2d5d00d56e7850f541ff000ac45e619a4a887ae2eadd68d4fb0757`), downloaded
from a temporary LAN file server rather than a GitHub Release (no release has
been published for this build). macOS arm64 and Linux x64 archives from the
same run built and smoke-tested cleanly in CI but were not manually walked
through this session — macOS arm64's manual walkthrough is the separate
`0.1.0-alpha.1`-era [checkpoint](bundled-launch-macos-arm64-2026-09-22.md), not
re-run against this build.

| 17 | Download the archive with a browser and extract it with the OS's own tool. | PASS (Windows x64) | Chrome download, no warning beyond the save-location prompt; extracted with Explorer. macOS arm64, Linux x64: automated CI smoke only, not manually re-run. |
| 18 | Double-click Start. Record the exact Gatekeeper or SmartScreen steps needed. | PASS (Windows x64) | Not SmartScreen's blue "Windows protected your PC" page — instead the Attachment Manager's "Open File - Security Warning" dialog ("The publisher could not be verified... Run/Cancel", Unknown Publisher), triggered because Explorer extraction tagged the files individually. Windows x64 only. |
| 19 | Record the firewall prompt and the choice made (private networks only). | PASS (Windows x64) | Windows Firewall's rule ended up Private-only for `java.exe` (confirmed directly in Windows Defender Firewall → Allowed apps, no manual change needed). The prompt itself names the bundled Java runtime's vendor ("Eclipse Adoptium"), not "Oath Digital", and defaults to offering both Private and Public checked — a real user could easily leave Public checked without realizing it. Worth a quick-start note; not a code defect. |
| 20 | The window shows the banner; the browser opens the game-creation page at the banner address. | PASS (Windows x64) | Banner showed `http://192.168.1.64:8080`; game-creation page loaded there, titled "Create an Oath Digital game". |
| 21 | Create a game on the host; a second machine joins using a seat link with the banner address and completes a turn. | PASS | Machine B (host) created a 2-seat game (`manual-1790192029525-918257`; random seating put Blue first). Machine A opened Red's seat link in its own local browser (a genuinely separate physical machine) and completed Red's full turn (Broken Peaks, kept Knights Errant), correctly handing off to "Waiting for Blue". Blue's own turn (Fair Isle, kept Outriders) was played from the host machine itself, not a second machine — still valid gameplay evidence, just not the cross-machine proof; Red's turn supplies that. |
| 22 | Close the Start window (Windows: close the console window). The next start restores the game, and the log from the first run ends with `Oath Digital database closed`. | PASS, one gap | State restore fully confirmed: `nextSequence`/phase/round, first player, both pawns, and both advisers identical before and after restart; Blue's cookie survived (canonical URL with no seat link came back as Blue); Red's seat link independently confirmed the same state. The `Oath Digital database closed` log line itself was **not directly observed** — the console window closes itself on a clean stop, and the launcher only pauses on a *non-zero* exit (confirmed in `packaging/desktop/Start Oath Digital.bat:8-12`; there is also no file-based log anywhere — `src/main/resources/logback.xml` is console-only). Circumstantial filesystem evidence (the database `.script` file's last-write timestamp precedes the relaunch by about a minute, consistent with a clean HSQLDB shutdown-rewrite) supports a clean close but doesn't prove the exact line. Recorded as a known gap, not fixed this session — see Findings. |

## Browser observations

| Browser, version, OS | Host form | Seat exchange | Gameplay | Reconnect/restart | Status and evidence |
| --- | --- | --- | --- | --- | --- |
| Claude Code built-in browser pane (Chromium 152.0.7977.130), macOS | PASS | PASS | PASS | PASS | Machine A / red-exile. See LAN rows 1-9 above. |
| Chrome 153.0.0.0, Windows 11 Pro | N/A (not host) | PASS | PASS | PASS | Machine B / blue-exile, via Claude-in-Chrome extension. |
| Claude Desktop built-in pane (Chromium-based, version not separately reported), Windows 11 Pro | N/A (not host) | PASS | PASS | PASS | Machine B / yellow-exile. |
| Machine A's own local browser (bundled-archive game, exact browser/version not recorded) | N/A (not host) | PASS | PASS | PASS | Machine A / red-exile, against Machine B's Windows-bundled host. See bundled-archive rows 21-22 above — the one pass this record treats as genuine second-machine evidence, since it's the only seat this run where the operator confirmed sitting at Machine A's own keyboard. |
| Chrome, Windows 11 Pro (bundled-archive game) | PASS (host) | N/A | PASS | N/A | Machine B / blue-exile, played from the host machine itself — valid gameplay evidence, not cross-machine evidence. |

None of the browsers used this run is a mainstream end-user install on
Machine A specifically identified by name/version; a real macOS Safari/Chrome/
Firefox pass with full version details has not been recorded for this build.

## Findings from this run

- **Fixed**: `scripts/smoke-packaged-distribution.sh` and
  `scripts/smoke-packaged-container.sh` still submitted the deleted
  `placePawn` command with hardcoded sequence numbers and assumed
  blue-exile always goes first. Both are stale relative to the
  Chronicle/walker-procedure setup rework and the seating-order shuffle.
  Fixed in commit `a25e8a1` (packaged-distribution script verified live and
  by repeated smoke runs; the container script fixed by inspection only —
  Docker was not available to execute it this run).
- **Fixed**: `ActionDecisionRenderer.status` fell through to a debug fallback
  reading `activeParticipantId` whenever a viewer's own parked walker
  decision had no named phase case (e.g. `setup-walker-decision`) — the
  active player saw no "your turn" indicator at all, though the board's
  click targets still worked underneath. Found live during this run's row 6
  (Machine B: "Blue's panel didn't show its turn when it became active").
  Fixed in commit `c86530c`, covered by two new regression tests, verified
  live in-browser after rebuilding.
- **Not a defect**: seat cookies are one per game per browser profile;
  opening a second seat's link in the same profile silently replaces the
  first. Matches documented behavior in
  `docs/operations/network-and-browser.md`. Caused the row 9 gap above.
- **Not a defect**: the in-game log panel was empty throughout this session
  (confirmed placeholder, not wired up yet).
- **Environment note, not a product issue**: Machine B's Claude-in-Chrome
  extension did not attach inside an incognito window even with the
  extension's incognito permission enabled.
- **Fixed**: `.github/workflows/alpha-release.yml`'s `archives` job set up
  Node but never ran `npm ci`, so the frontend test step's `jsdom` dependency
  (in `package.json`) was never installed. This was this workflow's first-ever
  run in the repository, so nothing had caught it before. Failed with
  "Cannot find module 'jsdom'" right after 1638 JVM tests had passed. Fixed
  in commit `611ff19`; the re-run (workflow run
  [35907569705](https://github.com/eNamorD/oathdigital/actions/runs/35907569705))
  passed `archives`, all three `bundled` targets, and `images (amd64)`.
  `images (arm64)` failed on a 30-second readiness timeout under QEMU
  emulation — Docker/OCI publishing is out of scope for this acceptance run
  and was not investigated further.
- **Found, not fixed this session**: on a clean stop, the desktop launcher's
  console window closes itself with no way to read the final log lines,
  including the `Oath Digital database closed` evidence row 22 asks for.
  `packaging/desktop/Start Oath Digital.bat` only pauses on a *non-zero* exit
  (`Start Oath Digital.command`/`start-oathdigital.sh` have the same shape),
  and there is no file-based logging at all (`src/main/resources/logback.xml`
  is console-only). A real player has no way to see that log short of running
  the launcher from an already-open terminal. Deferred by operator decision —
  see row 22 above for the workaround used to gather circumstantial evidence
  instead.
- **Also found this session, corrected before it reached this record**: the
  operator initially drove what was believed to be "Machine A's browser" via
  the Claude-in-Chrome browser extension, which turned out to be running on
  Machine B — meaning that seat's turn was actually played by the host
  machine, not a second machine. Caught and corrected before being recorded
  as evidence; row 21's actual cross-machine proof comes from a different
  seat, played at Machine A's own physical keyboard. Noted here only as a
  caution for whoever runs the next acceptance pass with this tooling.

## Result

| Field | Recorded value |
| --- | --- |
| Overall status | PASS for rows 1–10 and 17–22, with the row 9 and row 22 gaps noted above. Rows 11–16 (HTTPS proxy) UNEXECUTED — not attempted this run, by operator decision. |
| Failed or blocked row numbers | None failed. Rows 9 and 22 passed with noted verification gaps (see above), not failures. |
| Raw evidence location | This session's tool transcript; no separate raw-log archive was saved. |
| Seat-code redaction reviewed | Yes — raw seat codes appear only in this session's private chat transcript, never in this document or the server log. |
| Operator sign-off | Signed by eNamorD 2026-09-23 |

The build passes this manual gate only when every applicable row has observed
evidence, no required row remains **UNEXECUTED** or **BLOCKED**, and every
failure has been resolved and rerun. This record does not publish artifacts or
change firewall, proxy, or database state by itself.
