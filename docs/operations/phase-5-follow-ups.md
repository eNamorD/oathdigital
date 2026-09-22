# Phase 5 distribution and runtime — recorded follow-ups

Items the Phase 5 whole-branch review raised that were deliberately not fixed on
`feat/phase-5-alpha-readiness`. None blocks the alpha branch merge; each was
adjudicated and recorded rather than silently dropped. In a source checkout,
`docs/superpowers/plans/2026-09-07-phase-5-distribution-runtime.md` records the
follow-up ownership; source-only plan files are not bundled with these operator
documents.

## Final release-operations evidence — local only

Task 2 evidence at commit `5b817f6` recorded a macOS arm64 Java
`21.0.12.1+1-LTS` build of `0.1.0-alpha.1`: 563 JVM tests and 137 frontend
tests passed, and separately extracted ZIP and TGZ artifacts each passed the
Universal smoke. Exact command, artifact checksums, and smoke scope are in the
[acceptance record](alpha-acceptance.md). A source checkout also contains the
source-only evidence report at
`.superpowers/sdd/2026-09-09-phase-5-release-operations/task-2-report.md`.

Task 3 made no code or package changes, so it reuses that exact evidence rather
than rerunning broad suites. On 2026-09-10, read-only
`docker info --format '{{.ServerVersion}} {{.Architecture}}'` could not connect
to `/Users/roman/.docker/run/docker.sock`: socket does not exist. Therefore no
live local container build, load, or smoke was run for this evidence update.
The earlier Docker 29.7.2 arm64 snapshot smoke below remains useful process
evidence, but is not a release-build or two-architecture release gate.

Still required before release:

- A per-build [LAN/TLS record](alpha-acceptance.md) for the exact candidate
  that is released. One was completed for the local `0.1.0-alpha.1` build on
  2026-09-21 (see `docs/testing/alpha-acceptance-0.1.0-alpha.1.md` in a source
  checkout; it is not bundled). It passed with a local throwaway CA and one
  NGINX log mitigation. Any release build that differs from that commit needs
  its own record.
- Docker/Buildx with QEMU or equivalent GitHub-hosted runners to build, load,
  and smoke `linux/amd64` and `linux/arm64` for exact candidate tag.
- GitHub repository remote, reviewed existing prerelease tag, Actions and GHCR
  enabled, protected tag policy, and publisher credentials with `contents:
  write` and `packages: write` only in publishing job. First run workflow with
  `publish=false`; inspect artifacts. Only then run same tag with `publish=true`
  after all gates pass.

No GitHub Actions run, GHCR push, manifest publication, GitHub prerelease,
firewall change, or existing-database change has been performed. A local NGINX
proxy was run on the operator's machine for the 2026-09-21 acceptance run only.

## Container release gate — now executed and passing

Resolved on 2026-09-08. `scripts/smoke-packaged-container.sh` had never run
anywhere while Docker was unavailable; it has now passed on Docker 29.7.2
(arm64) against `oathdigital:0.1.0-SNAPSHOT`, covering readiness, index, asset,
restart, and shutdown. Three claims that were previously argued only from the
generated Dockerfile are now confirmed against a running container:

- **The image starts on its own defaults.** With only `OATH_PUBLIC_BASE_URL`
  supplied, the server reaches ready and the database is created at
  `/var/lib/oathdigital/database.*`, owned by `oathdigital`. Nothing is written
  under `/opt/docker/var`, the unwritable path the pre-fix image resolved to.
- **The container runs as a non-root user.** `id` inside the running container
  reports `uid=10001(oathdigital) gid=0(root)`, and that user owns its data
  directory.
- **The shutdown assertion has teeth.** The `docker logs --since` window was
  negative-tested: after a graceful `docker restart` (which emits one close
  marker) followed by an ungraceful `docker kill`, the window contains zero
  close markers while the full log contains one. An ungraceful final stop
  therefore fails the gate, which is the defect the window was added to fix.

`docker run` with *no* environment at all still exits 2 with the single line
`oathdigital: --public-base-url: required for non-loopback trusted-alpha
binding` and no stack trace — the documented and intended behaviour, since the
default `0.0.0.0` bind is non-loopback under `trusted-alpha`.

Re-run this gate on each alpha build; it is not part of `smokeUniversal`.

## Multi-architecture OCI build — workflow prepared, remote execution pending

The manual [alpha release workflow](releases.md) now stages the tested JVM
distribution, builds and loads separate Buildx images for `linux/amd64` and
`linux/arm64`, and runs the container smoke on each. Optional publication reloads
those tested image artifacts instead of rebuilding. Archive verification and
both architecture jobs must pass before publication can start; publishing is
disabled by default. Remote Actions/GHCR execution is still pending.
`Docker/publishLocal` continues to produce a single image for the host architecture.

The versioned extraction-root defect (former item 17) is resolved by setting
the Universal package name to `oathdigital-<version>` and removing the redundant
archive rename. Release tags and version overrides are validated; ordinary
local builds retain `0.1.0-SNAPSHOT`.

## Deferred defects

| # | Where | Item |
| --- | --- | --- |
| 7 | `ServerConfig.scala` `isLoopback` | Bracketed IPv6 loopback `[::1]` is rejected although bare `::1` is accepted. Normalize by stripping surrounding brackets in `parseHost`. |
| 12 | `build.sbt` resource generator | `Compile / resourceGenerators` puts `frontend / fullLinkJS` on the test classpath, so every `./sbtw test` pays the slowest build step. Scope the generator behind a packaging flag. |
| 13 | `src/test/resources/oathdigital/frontend/index.html` | The test fixture shadows the generated resource indistinguishably, so the suite cannot detect a wrong generated `index.html`. Add a fixture-only marker and assert on it. |
| 15 | `build.sbt` docker settings | The scoped/unscoped `dockerEnvVars` and `dockerExposedVolumes` pairs look redundant but are **load-bearing** — sbt-native-packager renders from the unscoped keys, and removing them silently drops the `ENV` and `VOLUME` lines. `verifyPackageMappings` catches it. Do not "simplify" without reading that gate. |
| 16 | `build.sbt` `verifyPackageMappings` | The Dockerfile ordering assertion hardcodes the literal `oathdigital:root` instead of deriving it from `daemonUser`/`daemonGroup`. A plugin version emitting `chown -R 10001:0` would fail the build loudly, not pass silently. |
| 18 | Docker image | No `HEALTHCHECK`. `/health/ready` is the right probe; the `-jre` base image has no `curl`, so this needs a `wget` or Java-based probe. |

## Observed during LAN acceptance (2026-09-21) — not yet investigated

Recorded by the operator while running the `0.1.0-alpha.1` LAN acceptance.
Neither item blocks the acceptance run. Neither has been diagnosed.

- **Earlier UI work is not visible in this build.** The operator did UI work a
  while back and does not see those changes in the packaged build. Investigation
  is deferred. Start by identifying which UI changes are expected, then compare
  the source against the served frontend (`frontend/production-index.html`,
  `frontend/styles.css`, the linked `main.js`) to see whether they were never
  merged, were lost in a merge, or are not served in trusted-alpha mode.
- **Reconnection UX after a disconnect.** After a client loses its connection,
  it should retry with exponential backoff and show a **Try Again** button so
  the player can retry immediately. On 2026-09-21 the operator disconnected
  and reconnected machine B and it reconnected successfully; the behavior
  during the outage (automatic retry, backoff, manual retry control) was not
  recorded against this expectation.

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

## Game-creation page redesign — done

The host form's **Seat definitions** textarea, **Game ID** field and **First
player ID** field are gone. The page loads with a red and a blue player. An
**Add a Player** menu offers the untaken lineage colors in the order red, blue,
yellow, white, black, pink, brown, up to six players. Each row has an editable
player ID, defaulting to the capitalized color name, and a **Remove** button.
Creation needs at least two players. The page derives each lineage ID as
`<color>-lineage` and generates the game ID in the browser. The trusted
creation request no longer carries `firstPlayerId`, because the server shuffles
the seating and picks the first player. In-game badges now cover white, black,
pink and brown. Deferred server-side follow-ups are in the roadmap.

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
