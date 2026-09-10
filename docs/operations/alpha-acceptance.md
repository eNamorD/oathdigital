# Per-build trusted-alpha acceptance record

Copy this file for each candidate build. A blank template is not evidence.
Every row below starts as **UNEXECUTED** and must remain so until the named
check is observed on that build. Use **PASS**, **FAIL**, or **BLOCKED** only with
dated evidence and operator initials.

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
| 15 | Normal exchange and a controlled failing `/s/` request use a disposable test code; every NGINX access/error log, upstream application log, and applicable proxy, CDN, firewall, WAF, agent, or dashboard log is inspected, and no raw `/s/{seat-code}`, `Cookie`, or `Set-Cookie` value appears. | UNEXECUTED | Not observed. |
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
