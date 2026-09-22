# Per-build trusted-alpha acceptance record

Copy this file for each candidate build and store the completed copy under
`docs/testing/`. A blank template is not evidence.
Every row below starts as **UNEXECUTED** and must remain so until the named
check is observed on that build. Use **PASS**, **FAIL**, or **BLOCKED** only with
dated evidence and operator initials.

## Recorded automated evidence (not manual acceptance)

At commit `5b817f65e13c9561b597c743e746c749c84fa3e3`
(`build: prepare gated multiarchitecture alpha releases`), Task 2 ran the
following locally on macOS arm64 with Temurin Java `21.0.12.1+1-LTS`, Node
`v24.19.0`, and `OATH_RELEASE_VERSION=0.1.0-alpha.1`:

```sh
export JAVA_HOME='/tmp/oath-release-java21.xc372r/jdk-21.0.12.1+1/Contents/Home'
export PATH="$JAVA_HOME/bin:/Users/roman/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
export OATH_RELEASE_VERSION=0.1.0-alpha.1
./sbtw verifyReleaseVersion test frontend/test verifyPackageMappings Universal/packageBin Universal/packageZipTarball Docker/stage
```

The command exited 0: **563 JVM tests** (67 XML suites) and **137 frontend
tests** (9 XML suites), all with zero failures and zero errors. It produced
`oathdigital-0.1.0-alpha.1.zip` (SHA-256
`4bd90e5f1e2416ab4fc63ae13ab3ceb407d46f67b225288e0776779678e7369e`) and
`oathdigital-0.1.0-alpha.1.tgz` (SHA-256
`0fba4eb5841c54f36a31c9677f6cb9a5efeacda6f5f539c57399cf022baa7c00`). Each
archive was extracted into its own temporary directory and passed
`sh scripts/smoke-packaged-distribution.sh <extracted-root> 18080` using that
Java 21 runtime. Both smokes observed readiness, frontend assets, three private
seats, a command, seat restoration across restart, database close, and shutdown.

This is automated archive evidence only. Source:
`.superpowers/sdd/2026-09-09-phase-5-release-operations/task-2-report.md` at
commit `5b817f6`. It does not satisfy any LAN, browser, proxy, TLS, or
publication row below.

## Build and environment

| Field | Recorded value |
| --- | --- |
| Build version or release tag | UNEXECUTED — not recorded |
| Git commit | UNEXECUTED — not recorded |
| Artifact and SHA-256 | UNEXECUTED — not recorded |
| Distribution type | UNEXECUTED — Universal ZIP/TGZ or OCI image not recorded |
| Host OS and architecture | UNEXECUTED — not recorded |
| Java version for Universal | UNEXECUTED — not recorded or not applicable |
| OCI image platform | UNEXECUTED — not recorded or not applicable |
| Database directory or named volume | UNEXECUTED — not recorded |
| Public base URL | UNEXECUTED — not recorded |
| Machine A OS, browser, version | UNEXECUTED — not recorded |
| Machine B OS, browser, version | UNEXECUTED — not recorded |
| Machines and network are separate | UNEXECUTED — not confirmed |
| Operator, date, time zone | UNEXECUTED — not recorded |

Machine A and Machine B must be different machines on the tested network.
Separate tabs, windows, or browser profiles on one machine do not satisfy the
two-machine requirement.

## LAN and multiplayer checks

| # | Acceptance check | Status | Evidence or defect |
| ---: | --- | --- | --- |
| 1 | From Machine A, `/health/live` and `/health/ready` succeed at the recorded public base URL. | UNEXECUTED | Not observed. |
| 2 | Machine A opens `/` and creates one game through the host form with exactly three distinct seats. | UNEXECUTED | Not observed. |
| 3 | Host result shows three assigned `/s/` links, each is copied privately, and no raw code appears in application logs. | UNEXECUTED | Not observed. |
| 4 | Machine A opens seat 1 in one browser profile; Machine B opens seat 2; a separate profile opens seat 3. Each redirects to the same game's canonical `/games/{game-id}` URL. | UNEXECUTED | Not observed. |
| 5 | Each profile displays the correct private viewer and does not display another seat's private view. Record player IDs and screenshots with seat codes removed. | UNEXECUTED | Not observed. |
| 6 | All three seats perform representative multiplayer turns. Record action names, player IDs, and resulting sequence numbers. | UNEXECUTED | Not observed. |
| 7 | Machine B disconnects from the network, reconnects, reloads the canonical game URL, and resumes its assigned seat at authoritative current state. | UNEXECUTED | Not observed. |
| 8 | All clients close. Operator stops the server cleanly, confirms the database-close log, and restarts the same build against the same database directory or volume. | UNEXECUTED | Not observed. |
| 9 | After restart, all three canonical URLs load with retained cookies, show the correct private views, and retain the previously recorded sequence/state. | UNEXECUTED | Not observed. |
| 10 | In a disposable profile, clearing the game cookie causes the canonical URL to show recovery guidance; reopening the saved original seat link restores only that seat. | UNEXECUTED | Not observed. |

## HTTPS proxy and origin checks

| # | Acceptance check | Status | Evidence or defect |
| ---: | --- | --- | --- |
| 11 | The external origin uses a valid HTTPS certificate and the backend listens only on a restricted interface that is not directly reachable from Machine B. | UNEXECUTED | Not observed. |
| 12 | A newly generated seat link uses the exact configured HTTPS scheme, host, and port. | UNEXECUTED | Not observed. |
| 13 | Visiting the seat link stays on the HTTPS origin and returns an `oath_seat` cookie with `Secure`, `HttpOnly`, `SameSite=Lax`, and the exact `/games/{game-id}` path. | UNEXECUTED | Not observed. |
| 14 | A same-origin gameplay request succeeds; a deliberately mismatched `Origin` request is rejected without changing game state. | UNEXECUTED | Not observed. |
| 15 | Normal exchange, a controlled failing `/s/` request, and a `/s/` request with the backend unreachable (on a throwaway proxy instance) use a disposable test code; every NGINX access/error log, upstream application log, and applicable proxy, CDN, firewall, WAF, agent, or dashboard log is inspected, and no raw `/s/{seat-code}`, `Cookie`, or `Set-Cookie` value appears. | UNEXECUTED | Not observed. |
| 16 | Forwarded identity headers do not select or change a seat; the seat cookie remains the only trusted seat identity. | UNEXECUTED | Not observed. |

## Browser observations

| Browser, version, OS | Host form | Seat exchange | Gameplay | Reconnect/restart | Status and evidence |
| --- | --- | --- | --- | --- | --- |
| UNEXECUTED | UNEXECUTED | UNEXECUTED | UNEXECUTED | UNEXECUTED | Not observed. |
| UNEXECUTED | UNEXECUTED | UNEXECUTED | UNEXECUTED | UNEXECUTED | Not observed. |

## Result

| Field | Recorded value |
| --- | --- |
| Overall status | UNEXECUTED |
| Failed or blocked row numbers | UNEXECUTED — not assessed |
| Raw evidence location | UNEXECUTED — not recorded |
| Seat-code redaction reviewed | UNEXECUTED — not reviewed |
| Operator sign-off | UNEXECUTED — not signed |

The build passes this manual gate only when every applicable row has observed
evidence, no required row remains **UNEXECUTED** or **BLOCKED**, and every
failure has been resolved and rerun. This record does not publish artifacts or
change firewall, proxy, or database state by itself.
