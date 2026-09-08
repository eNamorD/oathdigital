# Packaged artifact smoke tests

These tests exercise copied Universal package contents and a locally built OCI
image. They verify readiness, the packaged index and JavaScript asset,
persistent database startup, and bounded shutdown without using source-tree
frontend files. Run them before publishing alpha artifacts.

## Prerequisites

The Universal test requires Java 21 and `curl`. The container test requires
`curl`, the Docker CLI, and a reachable Docker daemon. Docker unavailability is
a failed or unrun environment-dependent gate; it is not a successful smoke
test.

Choose unused loopback ports. Each script exits non-zero on a failed check and
prints captured server logs only on failure.

## Universal distribution

Prepare the staged package and verify its mappings:

```sh
./sbtw smokeUniversal
```

The alias does not run the process-level test. Run this required follow-up from
the repository root:

```sh
scripts/smoke-packaged-distribution.sh target/universal/stage 18080
```

The script copies only the staged package contents into a directory created by
`mktemp -d`, launches that copy in `trusted-alpha` mode, and stores its HSQLDB
files beside the copy. After HTTP checks, it sends `TERM`, allows at most 15
seconds for shutdown, accepts the JVM's normal `0` or `143` termination
status, verifies database files and an explicit database-close log entry, and
removes only its exact temporary directory. HTTP requests use bounded connect
and total timeouts; readiness requests share the 30-second readiness budget.

The packaged launcher does not require sbt or Node after staging. `JAVA_HOME`
may be set explicitly when Java is not discoverable through `PATH`.

## OCI image

Build the local image and verify package mappings:

```sh
./sbtw smokeContainer
```

Then run the required process-level follow-up:

```sh
scripts/smoke-packaged-container.sh oathdigital:0.1.0-SNAPSHOT 18081
```

The script checks Docker before creating resources. It creates one uniquely
named container and one uniquely named volume, publishes only
`127.0.0.1:18081:8080`, waits up to 30 seconds for readiness, checks the index
and asset, and restarts the same container with the same volume. It rechecks
readiness, stops the container with a 15-second timeout, and removes only those
two named resources through its cleanup trap. It accepts the JVM's normal `0`
or `143` termination status only when the container logs explicit database-close
evidence. HTTP requests use bounded connect and total timeouts.

## Build all Universal alpha archives

Run JVM and frontend tests before creating both versioned archives:

```sh
./sbtw buildAlphaArtifacts
```

This alias intentionally excludes Docker. Container smoke testing remains an
explicit operator action because it depends on the local Docker runtime.
