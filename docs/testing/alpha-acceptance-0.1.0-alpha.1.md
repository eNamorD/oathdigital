# Trusted-alpha acceptance record: 0.1.0-alpha.1

Completed copy of the [per-build acceptance template](../operations/alpha-acceptance.md)
for the local candidate build `0.1.0-alpha.1`, run on 2026-09-21 (Pacific
Daylight Time). Statuses use only **PASS**, **FAIL**, and **BLOCKED**, and each
row says how it was observed. Observations by the operator are marked
*operator*. Observations from commands run by Claude on the host during the
session are marked *host check*.

This record covers the Universal `.tgz` archive over a private LAN and behind a
local HTTPS proxy. It does not cover OCI images, GitHub Actions, GHCR, or the
GitHub prerelease; those remain open in the [roadmap](../ROADMAP.md).

## Build and environment

| Field | Recorded value |
| --- | --- |
| Build version | `0.1.0-alpha.1` (`OATH_RELEASE_VERSION=0.1.0-alpha.1`) |
| Git commit | `13d4118cedddb8a61f901e079ed0ea850c53cf21` (clean tree; archives built after this commit) |
| Artifact and SHA-256 | `oathdigital-0.1.0-alpha.1.tgz`, `e4789ed1cb5894694d5335936206b9ae623095f46a940e4ac204952822399578`. The `.zip` was built too (`363bbd981a25e43df0b8c2ae2d077e85559830d9baa48a2fe4788df9ced9f55c`) but not used. |
| Extracted tree | Byte-identical to the archive (`diff -r` against a scratch extraction) |
| Distribution type | Universal `.tgz` archive |
| Host OS and architecture | macOS 26.6.2, arm64 (Machine A) |
| Java version for Universal | OpenJDK 21.0.12.1 LTS, from the repository's `.tooling` directory. The system `java` was 26 and was not used. |
| OCI image platform | Not applicable |
| Database directory | `~/OathDigitalData/accept-1` (LAN, HTTP); `~/OathDigitalData/accept-tls-1` (HTTPS) |
| Public base URL | LAN: `http://192.168.1.193:8080`. HTTPS: `https://oath.lan.test` (NGINX 1.31.6 on port 443, backend `127.0.0.1:8080`) |
| Machine A OS, browser, version | macOS 26.6.2 (host). Seat 1: Firefox 155.0.1. Seat 3: Safari 26.6.2 (21624.5.1.11.3). |
| Machine B OS, browser, version | Windows (edition and build not recorded). Seat 2: Firefox 155.0.1. Throwaway profile: Chrome 153.0.8010.48. |
| Machines and network are separate | Yes. Machine A is `192.168.1.193`. The proxy access log shows Machine B's traffic from `192.168.1.64`, with Windows Chrome 153 and Firefox 155 user agents. |
| Operator, date, time zone | Roman (operator sign-off pending, see Result); 2026-09-21; PDT |

Automated evidence for this artifact (563 JVM tests, 137 frontend tests, and the
Universal smoke) was recorded earlier in the
[template's automated-evidence section](../operations/alpha-acceptance.md#recorded-automated-evidence-not-manual-acceptance)
at commit `5b817f6`. After the `main` merge, the commit above passed 1516 JVM
tests and 210 frontend tests, `verifyReleaseVersion`, and `verifyPackageMappings`,
and the extracted `.tgz` passed `scripts/smoke-packaged-distribution.sh` (host
check).

## LAN and multiplayer checks

Game `accept-1`, three seats (`p1` red, `p2` blue, `p3` white), first player
`p1`, over plain HTTP.

| # | Acceptance check | Status | Evidence or defect |
| ---: | --- | --- | --- |
| 1 | Health endpoints succeed from Machine A at the public base URL. | PASS | Host check: `/health/live` and `/health/ready` returned 200 on `127.0.0.1` and `192.168.1.193`. Operator: Machine B also saw `live` and `ready`. |
| 2 | Machine A creates one game through the host form with three distinct seats. | PASS | Operator. The journal holds three seat digests for `accept-1`. |
| 3 | Three `/s/` links are shown and copied privately, and no raw code appears in application logs. | PASS | Operator confirmed all three links were saved privately. Host check: the application log has no `/s/`, `oath_seat`, cookie or token strings and no 22-character URL-safe tokens, before and after seat exchange, play, and restart. The database stores 64-hex-character digests only. |
| 4 | Seat 1 on Machine A, seat 2 on Machine B, seat 3 in a separate profile each redirect to `/games/accept-1`. | PASS | Operator. |
| 5 | Each profile shows the correct private viewer and no other seat's private view. | PASS | Operator. Each seat reported its own `viewerPlayerId` from `/games/accept-1/api`. |
| 6 | All three seats perform representative multiplayer turns. | PASS | Operator: `p1` placed a pawn at Ancient City and chose Arcane Armor; `p2` placed a pawn at Fair Isle and chose Errand Boy; `p3` placed a pawn at Broken Peaks and chose Birdsong; then `p1` performed Search, Travel, and Campaign. Sequence after play: 43 on all three seats. Host check of the journal: 17 accepted command batches, stream positions 1, 2, 3, 4, 5, 6, 8, 10, 13, 16, 20, 23, 25, 27, 32, 38, 43. Per-action sequence numbers were not mapped to events. |
| 7 | Machine B disconnects, reconnects, reloads, and resumes its seat. | PASS | Operator: seat 2 disconnected and reconnected successfully, and reload resumed the seat. Outage duration was not recorded. See the deferred reconnection-UX note. |
| 8 | Clients close, the server stops cleanly with the database-close log, and restarts against the same database. | PASS | Two stop cycles. First stop: the close line was not captured because Ctrl-C also killed `tee` in the pipeline that logged the server output; the database was nonetheless closed cleanly (`modified=no`, no `.lck` or `.log`, 43 events intact) and the restart at 16:16:31 reopened it without errors. Second stop: `2026-09-21 16:22:53,379 INFO ... Oath Digital database closed` was captured, no server process or listener remained, and the files were again clean. The launch command was then changed so that `tee` ignores SIGINT and appends to the log. |
| 9 | After restart, all three canonical URLs load with retained cookies, show correct private views, and retain state. | PASS | Operator. Host check: all three seats reported sequence 43 before the first stop, and the reopened database held the same 43 events, stream position 43, three seats, and four schema versions. |
| 10 | In a disposable profile, clearing the game cookie shows recovery guidance and reopening the original link restores only that seat. | PASS | Operator: the recovery page titled "Open your seat link" appeared ("Open the assigned seat link from your host to return to your game"), and the original link restored the seat. |

## HTTPS proxy and origin checks

Backend `127.0.0.1:8080` with `OATH_PUBLIC_BASE_URL=https://oath.lan.test`,
database `accept-tls-1`. NGINX 1.31.6 (user-prefix Homebrew on Machine A) listened on
443 with a certificate issued by a throwaway local CA (see Conditions). Games
`accept-tls` (operator, on Machine B) and `tls-probe` (host check, created through
the proxy) were used.

| # | Acceptance check | Status | Evidence or defect |
| ---: | --- | --- | --- |
| 11 | Valid HTTPS certificate; backend not directly reachable from Machine B. | PASS | Operator confirmed this row on Machine B (valid HTTPS padlock; backend port not reachable). Host check: TLS 1.3, certificate SAN `oath.lan.test`, chains to the throwaway CA; backend bound to `127.0.0.1:8080` only, and a request to `192.168.1.193:8080` was refused. |
| 12 | A new seat link uses the exact configured HTTPS origin. | PASS | Operator. Host check: every link from `POST /games` through the proxy began `https://oath.lan.test/s/` with a 22-character code, response `Cache-Control: no-store`. |
| 13 | The seat link stays on the HTTPS origin and returns a `Secure`, `HttpOnly`, `SameSite=Lax` `oath_seat` cookie scoped to `/games/{game-id}`. | PASS | Operator: DevTools attributes. Host check: `303` to `/games/tls-probe` and `Set-Cookie: oath_seat=<redacted>; Max-Age=31536000; Path=/games/tls-probe; Secure; HttpOnly; SameSite=Lax`. |
| 14 | A same-origin request succeeds; a mismatched `Origin` is rejected without changing state. | PASS | Operator: gameplay over HTTPS from Machine B in Chrome, and in Firefox as seat 2. Host check: `Origin: https://evil.example` and `Origin: http://oath.lan.test` both returned 403 `csrf-validation-failed` with the sequence unchanged at 1; a same-origin command returned 200 and moved it to 2. |
| 15 | Normal exchange, failing `/s/` requests, and a backend-unreachable `/s/` request leave no raw code, `Cookie`, or `Set-Cookie` value in any log. | PASS with mitigation | See below. |
| 16 | Forwarded identity headers do not select or change a seat. | PASS | Host check only, from Machine A through the proxy: `X-Forwarded-User`, `X-Remote-User`, `Remote-User` and `Authorization` naming `p1` with no cookie returned 403; a valid `p1` cookie plus headers naming `p2` still resolved to `p1`. |

### Row 15 detail

- **Normal exchange:** operator exchanges from Machine B (Chrome and Firefox) and host-check exchanges of `tls-probe` seats. Host check of the logs after all browser traffic:
  - NGINX access log (87 lines): no `/s/` (the `/s/` location has `access_log off`), no cookie strings, no query strings.
  - NGINX error log (55 lines): no `/s/`, cookie, `oath_seat`, or `authorization` strings, and no 22-character tokens.
  - Backend log: no `/s/`, cookie, or token strings.
- **Failing requests** (host check, live proxy): an unknown well-formed code (404), a malformed code (404), and a valid code with a query string (400) left no code in any log.
- **Backend unreachable** (host check, throwaway NGINX instance on port 8444 whose upstream was a closed port, using the same configuration as the live proxy):
  - **Defect found.** With the configuration from the docs as written then, NGINX's error log recorded the raw disposable code in both `request: "GET /s/<code> ..."` and `upstream: "http://.../s/<code>"`.
  - **Mitigation.** A location-scoped `error_log ... crit;` inside `location ^~ /s/`. Re-running the same test on a second throwaway instance left no code and no `/s/` in any log, while a `/health/ready` failure still logged normally. The mitigation was applied to the live proxy (validated with `nginx -t`, reloaded) and to [network and browser guidance](../operations/network-and-browser.md), and the failure path is now part of the template's row 15.
- **Not inspected:** no CDN, WAF, error-reporting agent, or hosting dashboard was in the path. Machine B's browser history necessarily holds the seat links.
- **Observation:** NGINX warned twice that a game projection response was buffered to a temporary file under `tls/tmp/proxy`. The warnings contain no code. The files hold private game views on disk until NGINX removes them.

## Browser observations

Observations from the rows above. "Not recorded" means the operator did not report that combination separately.

| Browser, version, OS | Host form | Seat exchange | Gameplay | Reconnect/restart | Status and evidence |
| --- | --- | --- | --- | --- | --- |
| Firefox 155.0.1, macOS 26.6.2 (Machine A, seat 1) | Not recorded | PASS (LAN) | PASS (LAN) | PASS (reload after restart) | Rows 4–6, 9. |
| Safari 26.6.2 (21624.5.1.11.3), macOS 26.6.2 (Machine A, seat 3) | Not recorded | PASS (LAN) | PASS (LAN) | PASS (reload after restart) | Rows 4–6, 9. |
| Firefox 155.0.1, Windows (Machine B, seat 2) | Not recorded | PASS (LAN and HTTPS) | PASS (LAN and HTTPS) | PASS (disconnect, reconnect, reload, restart) | Rows 4–7, 9, 14. The throwaway CA was imported through Firefox's own certificate store, as instructed, for HTTPS. |
| Chrome 153.0.8010.48, Windows (Machine B, throwaway profile) | Not recorded | PASS (HTTPS) | PASS (HTTPS) | Cookie-clear recovery PASS (row 10) | Rows 10–14. Trusts the CA through the Windows store. |

Firefox and Chrome were exercised on Windows and Firefox and Safari on macOS.
Mobile browsers, other Safari versions, and other Windows browsers were not
tested. The support table in the [network guidance](../operations/network-and-browser.md)
is not changed by this record.

## Conditions and deviations

- **Local CA, not public trust.** The certificate came from a throwaway CA
  generated with `openssl` for this run (30-day validity, SAN `oath.lan.test`).
  Machine B trusted it by importing `ca.crt` after checking its thumbprint, and
  resolved `oath.lan.test` through a `hosts` entry. This does not show that a
  publicly trusted certificate behaves differently. NGINX ran in a user-prefix
  Homebrew install on Machine A, with the docs' configuration, one added
  `error_log` line, and log and temporary paths redirected into
  `~/OathDigitalData/tls`.
- **Documentation changed after the build.** The row 15 mitigation, the template
  wording, and the `docs/testing/` location were added after the tested archive
  was built. The archive's bundled documents therefore lack them. The change is
  documentation only; rebuild before release so the bundled docs match.
- **Two operator observations are deferred, not investigated:** earlier UI work
  not visible in this build, and reconnection behavior (exponential backoff and a
  **Try Again** button). A request to redesign the game-creation page is also
  recorded. All three are in the [Phase 5 follow-ups](../operations/phase-5-follow-ups.md).
- **Host sleep.** The backend logged two HikariCP "thread starvation or clock
  leap" warnings (1m29s and 4m42s) during the HTTPS run, consistent with the Mac
  sleeping. No request errors were observed.
- **Row 16** was checked by `curl` from Machine A only, not from Machine B.

## Result

| Field | Recorded value |
| --- | --- |
| Overall status | PASS for rows 1–16 with the conditions above. Row 15 passes only with the `/s/` error-log mitigation. |
| Failed or blocked row numbers | None |
| Raw evidence location | On Machine A, not in the repository: `~/OathDigitalData/accept-1.server.log`, `~/OathDigitalData/accept-tls-1.server.log`, and `~/OathDigitalData/tls/logs/` (NGINX access and error logs). Journals: `~/OathDigitalData/accept-1` and `~/OathDigitalData/accept-tls-1`. |
| Seat-code redaction reviewed | Reviewed by Claude with `grep` over each log named above; the operator has not independently reviewed the logs. |
| Operator sign-off | Pending. Not signed. |

This record does not publish artifacts, change a firewall, or replace the
release-operations gates that remain open.
