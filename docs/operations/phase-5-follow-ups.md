# Phase 5 distribution and runtime — recorded follow-ups

Items the Phase 5 whole-branch review raised that were deliberately not fixed on
`feat/phase-5-alpha-readiness`. None blocks the alpha branch merge; each was
adjudicated and recorded rather than silently dropped. The follow-up plans named
in the [implementation plan](../superpowers/plans/2026-09-07-phase-5-distribution-runtime.md)
own the work.

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

## Multi-architecture OCI build — still unconfigured

Unchanged by Docker becoming available: `build.sbt` has no `dockerBuildCommand`
or buildx wiring, so `Docker/publishLocal` produces a single image for the host
architecture only. Docker Desktop's `desktop-linux` builder does now advertise
`linux/amd64` and `linux/arm64`, so the work is unblocked.
`phase-5-release-operations` owns it — see the note in the plan's Global
Constraints.

## Deferred defects

| # | Where | Item |
| --- | --- | --- |
| 7 | `ServerConfig.scala` `isLoopback` | Bracketed IPv6 loopback `[::1]` is rejected although bare `::1` is accepted. Normalize by stripping surrounding brackets in `parseHost`. |
| 12 | `build.sbt` resource generator | `Compile / resourceGenerators` puts `frontend / fullLinkJS` on the test classpath, so every `./sbtw test` pays the slowest build step. Scope the generator behind a packaging flag. |
| 13 | `src/test/resources/oathdigital/frontend/index.html` | The test fixture shadows the generated resource indistinguishably, so the suite cannot detect a wrong generated `index.html`. Add a fixture-only marker and assert on it. |
| 15 | `build.sbt` docker settings | The scoped/unscoped `dockerEnvVars` and `dockerExposedVolumes` pairs look redundant but are **load-bearing** — sbt-native-packager renders from the unscoped keys, and removing them silently drops the `ENV` and `VOLUME` lines. `verifyPackageMappings` catches it. Do not "simplify" without reading that gate. |
| 16 | `build.sbt` `verifyPackageMappings` | The Dockerfile ordering assertion hardcodes the literal `oathdigital:root` instead of deriving it from `daemonUser`/`daemonGroup`. A plugin version emitting `chown -R 10001:0` would fail the build loudly, not pass silently. |
| 17 | `build.sbt` `Universal / packageName` | Universal archives extract to an unversioned `oathdigital/` root, so two alpha builds collide when extracted side by side. |
| 18 | Docker image | No `HEALTHCHECK`. `/health/ready` is the right probe; the `-jre` base image has no `curl`, so this needs a `wget` or Java-based probe. |

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
