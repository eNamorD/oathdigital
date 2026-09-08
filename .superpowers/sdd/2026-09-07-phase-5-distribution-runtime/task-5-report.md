# Task 5 report: packaged artifact smoke tests

## Scope and inherited work

This task adds process-level smoke coverage for the staged Universal
distribution and locally built OCI image. The working tree already contained
the Task 5 implementation when this retry began. I reviewed and retained the
following changes:

- `scripts/smoke-packaged-distribution.sh` copies only the staged package into
  an exact `mktemp -d` directory, starts it in trusted-alpha mode, polls
  readiness, verifies index and asset delivery, verifies HSQLDB output, and
  bounds graceful shutdown.
- `scripts/smoke-packaged-container.sh` validates Docker before resource
  creation, uses unique explicit container and volume names, publishes only a
  loopback port, checks the restarted container, and removes only its own
  resources in its trap.
- `build.sbt` exposes `smokeUniversal`, `smokeContainer`, and
  `buildAlphaArtifacts` aliases. The process-level shell invocations remain
  explicit because Docker is environment-dependent.
- `docs/operations/packaged-smoke-test.md` documents prerequisites, required
  follow-up commands, bounded cleanup, and Docker limitations.
- `.gitignore` ignores `.package-smoke/` for any future local fallback.

## RED and GREEN evidence

The inherited implementation predates this retry, so a complete original
test-first history cannot be recreated without discarding working code. The
specified recoverable RED precondition was executed:

```text
$ scripts/smoke-packaged-distribution.sh /private/tmp/oathdigital-missing-stage 18080
packaged stage directory not found: /private/tmp/oathdigital-missing-stage
exit=1
```

GREEN evidence:

```text
$ ./sbtw smokeUniversal
[success] Total time: 1 s

$ JAVA_HOME="$PWD/.tooling/jdk-17.0.19+10/Contents/Home" \
    scripts/smoke-packaged-distribution.sh target/universal/stage 18080
packaged distribution smoke passed: readiness, index, asset, persistence, shutdown
```

The host has no system Java runtime. The first Universal run therefore failed
at launcher discovery with `No java installations was detected.` Setting
`JAVA_HOME` to the project-provisioned Java 17 runtime isolated this as an
environment issue and allowed the full staged-artifact smoke to pass. The
distribution contract and operator documentation still require Java 21; Java
21 was not available on this host.

## Commands and results

| Command | Result |
| --- | --- |
| `sh -n scripts/smoke-packaged-distribution.sh` | Passed. |
| `sh -n scripts/smoke-packaged-container.sh` | Passed. |
| `./sbtw verifyPackageMappings Universal/stage Universal/packageBin Universal/packageZipTarball Docker/stage` | Passed all available packaging validations and built Universal archives/Docker staging context. Docker-stage emitted expected warning because no Docker CLI is installed. |
| `./sbtw smokeUniversal` | Passed. |
| `JAVA_HOME="$PWD/.tooling/jdk-17.0.19+10/Contents/Home" scripts/smoke-packaged-distribution.sh target/universal/stage 18080` | Passed end-to-end. |
| `scripts/smoke-packaged-container.sh oathdigital:smoke 18081` | Exited 1 before resource creation: `Docker is required for packaged container smoke testing`. |
| `git diff --check` | Passed. |

## Files

- `.gitignore`
- `build.sbt`
- `scripts/smoke-packaged-distribution.sh`
- `scripts/smoke-packaged-container.sh`
- `docs/operations/packaged-smoke-test.md`

## Self-review

I checked argument validation, use of `curl --fail --silent --show-error`,
readiness polling rather than a fixed readiness sleep, loopback-only container
publication, bounded TERM/stop waits, failure-only log output, explicit
resource ownership, and traps that clean only the recorded temporary directory
or recorded Docker resources. The Universal run demonstrated readiness, index,
asset, HSQLDB persistence, and shutdown behavior from copied staged contents.

## Concerns

- Docker CLI/daemon is unavailable on this host, so `Docker/publishLocal` and
  live OCI smoke execution could not be performed. The container script's
  missing-Docker precondition was exercised and it created no resources.
- Host lacks Java 21. Universal smoke used the repository's provisioned Java
  17 runtime only to verify the staged process path. Run the documented smoke
  command with an installed Java 21 runtime before release acceptance.
