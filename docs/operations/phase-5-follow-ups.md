# Phase 5 distribution and runtime — recorded follow-ups

Open items from the Phase 5 distribution and runtime work, recorded rather than
silently dropped. The [roadmap](../ROADMAP.md) schedules them. A resolved item
is deleted from this file.

## Deferred defects

| # | Where | Item |
| --- | --- | --- |
| 7 | `ServerConfig.scala` `isLoopback` | Bracketed IPv6 loopback `[::1]` is rejected although bare `::1` is accepted. Normalize by stripping surrounding brackets in `parseHost`. |
| 12 | `build.sbt` resource generator | `Compile / resourceGenerators` puts `frontend / fullLinkJS` on the test classpath, so every `./sbtw test` pays the slowest build step. Scope the generator behind a packaging flag. |
| 13 | `src/test/resources/oathdigital/frontend/index.html` | The test fixture shadows the generated resource indistinguishably, so the suite cannot detect a wrong generated `index.html`. Add a fixture-only marker and assert on it. |
| 15 | `build.sbt` docker settings | The scoped/unscoped `dockerEnvVars` and `dockerExposedVolumes` pairs look redundant but are **load-bearing** — sbt-native-packager renders from the unscoped keys, and removing them silently drops the `ENV` and `VOLUME` lines. `verifyPackageMappings` catches it. Do not "simplify" without reading that gate. |
| 16 | `build.sbt` `verifyPackageMappings` | The Dockerfile ordering assertion hardcodes the literal `oathdigital:root` instead of deriving it from `daemonUser`/`daemonGroup`. A plugin version emitting `chown -R 10001:0` would fail the build loudly, not pass silently. |
| 18 | Docker image | No `HEALTHCHECK`. `/health/ready` is the right probe; the `-jre` base image has no `curl`, so this needs a `wget` or Java-based probe. |

## Reconnection UX after a disconnect

After a client loses its connection, it should retry with exponential backoff
and show a **Try Again** button so the player can retry immediately. On
2026-09-21 the operator disconnected and reconnected machine B and it
reconnected successfully; the behavior during the outage (automatic retry,
backoff, manual retry control) was not recorded against this expectation. The
frontend offers a manual **Reconnect** button but no exponential backoff.

## Snapshot polling sends a full projection on every tick

Not a defect; a cost the current design pays and could stop paying.

`SnapshotPollingCoordinator` reschedules a `setTimeout` every 5s while the tab
is visible and every 30s while it is hidden. Each tick calls `GameClient.load`,
which issues `GET /api/dev/first-games/{id}?playerId=…` and returns the entire
viewer-scoped projection. Nothing has usually changed, so the steady-state cost
of an idle game is a full projection per client per 5s.

The comparison that prompted this note: `haunt-roll-fail` polls the same way but
far more cheaply. Its client reschedules a 500ms `setTimeout` and reads an
append-only journal by index, so an idle tick returns an empty body. It can
afford 500ms because the common response costs nothing.

We cannot copy that directly. Its clients replay the whole journal locally and
therefore hold every player's hidden information, which is a trusted-client
model. `GamePresentationProjector` redacts per viewer, and the raw event
endpoint is deliberately withheld from trusted seats for that reason. Shipping
event deltas would require redacting a stream per viewer rather than a snapshot.

The available improvement keeps redaction intact: add a cheap change-check that
returns the current sequence for a viewer, or a 304, and fetch the full
projection only when the sequence advanced. That makes the idle tick nearly
free and would allow a much shorter interval without the bandwidth growing.
Sizing, measurement and endpoint shape are unspecified; this needs its own
design before anyone implements it.

## Documentation gaps

- `docs/operations/configuration.md` documents named and anonymous volumes but not
  host bind mounts at `/var/lib/oathdigital`, where the *host* directory's
  ownership — not the image's — decides whether UID 10001 can write.

## Deliberate deviations from the plan text

Two decisions during execution overrode what the plan prescribed, with the design
spec as the binding authority:

- **Asset cache policy.** The plan prescribed
  `public, max-age=31536000, immutable`, but never delivered the fingerprinting
  that makes it safe, and pins the literal filenames `main.js` and `styles.css`.
  Shipped instead: `public, max-age=0, must-revalidate`, relying on the existing
  ETag/Last-Modified. Content-hash fingerprinting remains the proper fix and
  would restore the long-lived header.
- **Configuration-error reporting.** The plan prescribed
  `throw new IllegalArgumentException`; the spec forbids stack traces for
  expected errors. Shipped instead: one stderr line per error and `sys.exit(2)`.
