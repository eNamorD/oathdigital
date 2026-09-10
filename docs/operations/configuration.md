# Runtime configuration

Oath Digital reads command-line options first, environment variables second,
and defaults last. A command-line value therefore overrides the matching
environment variable. Packaged launchers provide `trusted-alpha` and their
bundled catalog path only when `OATH_MODE` and `OATH_CATALOG_PATH` are absent;
explicit environment or command-line values still win.

Universal archives require Java 21 on the host. They do not require sbt or
Node. The OCI image bundles a Java 21 runtime and runs as the non-root
`oathdigital` user (UID 10001).

Start with the [trusted-alpha quick start](quick-start.md). Before operating on
persistent files, read the [data and upgrade policy](data-policy.md). LAN,
firewall, HTTPS proxy, and provisional browser guidance is in
[network and browser guidance](network-and-browser.md).

## Options

| Command-line option | Environment variable | Default |
| --- | --- | --- |
| `--host HOST` | `OATH_HOST` | `127.0.0.1` |
| `--port PORT` | `OATH_PORT` | `8080` |
| `--public-base-url URL` | `OATH_PUBLIC_BASE_URL` | unset |
| `--session-cookie-name NAME` | `OATH_SESSION_COOKIE_NAME` | unset; development mode only; must be supplied with authenticated public origin |
| `--authenticated-public-origin ORIGIN` | `OATH_AUTHENTICATED_PUBLIC_ORIGIN` | unset; development mode only; must be supplied with session cookie name |
| `--database-path PATH` | `OATH_DATABASE_PATH` | `var/oathdigital` |
| `--catalog-path PATH` | `OATH_CATALOG_PATH` | `docs/catalog/new-foundations-component-catalog.json`; packaged launcher uses bundled `share/oathdigital/new-foundations-component-catalog.json` |
| `--mode development\|trusted-alpha` | `OATH_MODE` | `development`; packaged launcher uses `trusted-alpha` |

`development` accepts loopback bind hosts only. `trusted-alpha` accepts a
non-loopback host only when `--public-base-url` or `OATH_PUBLIC_BASE_URL`
provides the browser-visible HTTP or HTTPS origin. Public base URLs cannot
contain credentials, a path, query, or fragment. Authenticated public origins
must use HTTPS except for loopback HTTP origins.

## Universal archive

Extract either versioned archive, enter its top-level directory, and run:

```sh
OATH_MODE=trusted-alpha \
OATH_HOST=0.0.0.0 \
OATH_PORT=8080 \
OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
OATH_DATABASE_PATH=/home/alex/oathdigital-data/alpha-1/database \
bin/oathdigital
```

The example data directory must exist and be writable by the process. For the
catalog bundled in the archive, omit `OATH_CATALOG_PATH` as shown. For loopback
use, omit `OATH_HOST` and `OATH_PUBLIC_BASE_URL`. Windows users can set the
same environment variables and run `bin\oathdigital.bat`; complete commands
for all three host operating systems are in the quick start.

## OCI image

The image sets `OATH_HOST=0.0.0.0` and
`OATH_DATABASE_PATH=/var/lib/oathdigital/database` itself and declares
`/var/lib/oathdigital` as a volume, so only the browser-visible origin has to
be supplied. After `./sbtw Docker/publishLocal`, publish the service on all
host interfaces for trusted LAN access:

```sh
docker run --rm --name oathdigital \
  --publish 8080:8080 \
  --env OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
  --volume oathdigital-data:/var/lib/oathdigital \
  oathdigital:0.1.0-SNAPSHOT
```

Name a volume as shown to keep the database across container replacements; the
declared volume otherwise becomes an anonymous one.

Stop the container with `docker stop --time 15 oathdigital`, wait for the
database-close log, and remove only the stopped container with
`docker rm oathdigital`. The named volume remains. Follow the data policy for
stopped-volume backup and matching-release restore.

For host-local access only, replace `--publish 8080:8080` with
`--publish 127.0.0.1:8080:8080` and use a loopback public base URL such as
`http://127.0.0.1:8080`.

The container launcher supplies `trusted-alpha` mode and the bundled catalog
path. Add `--env OATH_MODE=...` or `--env OATH_CATALOG_PATH=...` to override
them. Command-line options placed after the image name override environment
values.

`Docker/publishLocal` produces an image for the build host's own architecture.
The manual [alpha release workflow](releases.md) builds, loads, and smokes both
`linux/amd64` and `linux/arm64` before its optional publication job can run.
Publication is disabled by default; workflow implementation does not establish
that any registry image has been published.

Internet exposure requires HTTPS at a trusted reverse proxy. Use the concrete
proxy and log-redaction requirements in the network guidance, then record
separate-machine LAN and TLS results in the
[per-build alpha acceptance record](alpha-acceptance.md). An unexecuted record
is a template, not acceptance evidence.
