# Phase 5 distribution and runtime — recorded follow-ups

Items the Phase 5 whole-branch review raised that were deliberately not fixed on
`feat/phase-5-alpha-readiness`. None blocks the alpha branch merge; each was
adjudicated and recorded rather than silently dropped. The follow-up plans named
in the [implementation plan](../superpowers/plans/2026-09-07-phase-5-distribution-runtime.md)
own the work.

## Release gate that has never run

`scripts/smoke-packaged-container.sh` has not been executed anywhere. Docker was
unavailable on every verification host, so the container smoke test, the OCI
image's default environment, and the image's ability to start at all are argued
from the generated `target/docker/stage/Dockerfile` and the script text only.
**Run it on a Docker-capable host before any alpha release.** This is the single
highest-value outstanding gate.

Multi-architecture OCI build and publication (`linux/amd64`, `linux/arm64`) is
likewise unconfigured, not merely unverified — see the note in the plan's Global
Constraints. `phase-5-release-operations` owns it.

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
