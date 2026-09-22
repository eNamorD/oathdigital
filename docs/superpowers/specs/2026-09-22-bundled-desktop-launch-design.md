# Bundled runtime and desktop launch — design

Date: 2026-09-22
Status: approved design, not yet planned or implemented

## Goal

A trusted-alpha host should be able to run a server without installing Java
and without typing commands. The target flow is: download the archive for
your OS, extract it with the OS's own tool, double-click **Start**, and share
the address the window prints.

## Decisions

| Topic | Decision |
| --- | --- |
| Bundled platforms | macOS arm64, Windows x64, Linux x64 |
| All-platform archive | Kept unchanged; still requires Java 21 |
| Build approach | Post-process the existing Universal stage (no sbt `JlinkPlugin`, no `jpackage`) |
| Smart defaults | In Scala, behind `OATH_LAUNCH=desktop`; launcher scripts stay thin |
| Default exposure | LAN: bind all interfaces, detect the LAN address |
| Overrides | A properties file in the per-user app-data folder |
| First-run experience | Console window with a banner, plus automatic browser open |

Command-line and OCI runs keep their current behavior. Nothing new runs
unless `OATH_LAUNCH=desktop` is set.

## Evidence from the spike

On 2026-09-21 a throwaway `jlink` runtime built from Temurin 21.0.12.1+1
(macOS arm64) with the module list below was 52 MB, against 335 MB for the full
JDK. The runtime plus the staged app compressed to a 68 MB `.tgz`. With
`JAVA_HOME` pointing at that runtime and no Java on `PATH`, the staged
`bin/oathdigital` reached `/health/ready`, served the index page and
`main.js`, and logged `Oath Digital database closed` on SIGTERM.

`jdeps --print-module-deps` reported
`java.base,java.desktop,java.management,java.naming,java.sql,jdk.jfr,jdk.unsupported`.
The spike added `jdk.crypto.ec` and `jdk.localedata` (English only).
`java.desktop` is required by HSQLDB, Typesafe Config, and Slick.

## Distribution layout

Bundled archives are named `oathdigital-<version>-<target>.<ext>`, where
`<target>` is `macos-arm64`, `windows-x64`, or `linux-x64`, and `<ext>` is
`tgz` for macOS and Linux and `zip` for Windows. Each extracts to the same
single directory as the all-platform archive:

```
oathdigital-<version>/
  Start Oath Digital.command    macOS
  start-oathdigital.sh          Linux
  Start Oath Digital.bat        Windows
  bin/oathdigital               unchanged CLI launcher
  bin/oathdigital.bat           unchanged CLI launcher
  lib/  share/  conf/           identical to the all-platform archive
  jre/                          jlink runtime, bundled archives only
```

The Start files are included in the all-platform archive too, where they use
the host's Java 21.

## Components

### 1. Launcher runtime selection

The generated launchers already give a bundled runtime priority over
`JAVA_HOME`: the bash script checks `bundled_jvm` in `get_java_cmd`, and the
batch script checks `BUNDLED_JVM`. The project's `bashScriptExtraDefines` and
`batScriptExtraDefines` run before that check. Add one define to each that sets
the variable when `${app_home}/../jre/bin/java` (or `%APP_HOME%\jre\bin\java.exe`)
exists.

Consequences:

- In a bundled archive, the bundled runtime wins over `JAVA_HOME`, so a host
  with an unrelated Java 17 on `JAVA_HOME` still starts correctly.
- `-java-home <path>` still overrides the bundled runtime.
- The all-platform archive has no `jre/` and behaves as today.
- `verifyPackageMappings` gains an assertion that both defines are present in
  the rendered launchers.

### 2. Start files

Checked-in text files mapped into the Universal stage, not generated. Each:

1. Changes to its own directory.
2. Sets `OATH_LAUNCH=desktop`.
3. Runs `bin/oathdigital` (or `bin\oathdigital.bat`).
4. If the exit status is non-zero, waits for a key press so the error stays
   readable before the window closes.

The bash variants must keep their executable bit in the `.tgz`.
`verifyPackageMappings` asserts all three are mapped.

### 3. Bundling script

`scripts/package-bundled-runtime.sh`, POSIX `sh`, run by Git Bash on Windows.

Inputs: staged app directory, `JAVA_HOME` pointing at a Temurin 21 JDK,
target name, version, and a non-existent output directory.

Steps:

1. Read the module list from `packaging/jlink-modules.txt` (one module per
   line).
2. Run `jdeps --multi-release 21 --ignore-missing-deps --print-module-deps`
   over the staged `lib/` jars. Fail with a message naming each module that
   `jdeps` reports and the list lacks.
3. Run `jlink --add-modules <list> --include-locales en --strip-debug
   --no-man-pages --no-header-files --compress=zip-6 --output <dir>/jre`.
4. Copy the staged app beside `jre/` under `oathdigital-<version>/`.
5. On macOS, apply ad-hoc signing (`codesign --force -s -`) to the runtime
   binaries only if the plan's quarantine test shows it is needed.
6. Write the `.tgz` or `.zip` and print its SHA-256.

The script never downloads anything. The workflow supplies the JDK.

### 4. Desktop launch profile

A new unit in `oathdigital.server`, invoked by `OathServer` before
`ServerConfig.parse` only when `OATH_LAUNCH=desktop`. It produces an effective
environment map; `ServerConfig.parse` then runs unchanged, so all existing
validation and the trusted-alpha trust-boundary rule still apply.

OS calls (environment, OS name, network interfaces, default-route probe,
filesystem, process launch) are injected so the logic is unit-testable.

#### App-data folder

| OS | Folder |
| --- | --- |
| macOS | `~/Library/Application Support/OathDigital` |
| Windows | `%LOCALAPPDATA%\OathDigital` |
| Linux and others | `$XDG_DATA_HOME/oathdigital`, else `~/.local/share/oathdigital` |

The desktop default database path is `<app-data>/data/database`. The data is
outside the extracted application, so replacing the application with a new
version does not replace the database.

#### Settings file

Location: `<app-data>/oathdigital.properties`, Java properties format.

Recognized keys: `OATH_HOST`, `OATH_PORT`, `OATH_PUBLIC_BASE_URL`,
`OATH_DATABASE_PATH`, `OATH_OPEN_BROWSER`. Any other key is a configuration
error.

On startup, if the file does not exist, the profile writes a template in
which every key is commented out, each with a one-line explanation. It never
overwrites an existing file. If writing fails, it logs a warning and
continues. Because the template sets nothing, detected values keep following
the machine, for example when its LAN address changes.

#### Precedence

From highest to lowest:

1. Command-line flags.
2. Environment variables.
3. Settings file.
4. Desktop defaults: `OATH_HOST=0.0.0.0`, detected `OATH_PUBLIC_BASE_URL`,
   `OATH_DATABASE_PATH=<app-data>/data/database`, `OATH_OPEN_BROWSER=true`.
5. The existing built-in defaults in `ServerConfig`.

The profile fills a key only when no higher layer sets it. Command-line flags
are left in place for `ServerConfig.parse`, which already ranks them above the
environment. The profile must treat a key as set when either the flag or the
environment variable is present.

#### LAN address detection

1. Open an unconnected UDP socket and "connect" it to `192.0.2.1:9`. This
   sends no packets; the OS selects the source address of the default route.
   Use that address if it is a private IPv4 address (`10.0.0.0/8`,
   `172.16.0.0/12`, `192.168.0.0/16`).
2. Otherwise, use the first interface that is up, not loopback, and has a
   private IPv4 address.
3. Otherwise, set the host to `127.0.0.1`, set no public base URL, and show
   the no-LAN banner variant. The server still starts.

The chooser is a pure function over interface records and the probe result.
The detected public base URL is `http://<address>:<port>`, using the
effective port.

Detection runs only when no higher layer sets `OATH_PUBLIC_BASE_URL` and the
effective host is the wildcard `0.0.0.0`. If a higher layer sets `OATH_HOST`
to a loopback address, the profile sets no public base URL. If it sets a
specific non-loopback address, the profile uses `http://<that address>:<port>`
instead of detecting one.

#### Port

The default stays 8080. There is no automatic port change, because seat links
contain the port. If binding fails because the address is in use, the server
prints one line and exits with status 2:

```
oathdigital: port 8080 is in use. Is Oath Digital already running? Change OATH_PORT in <settings path>.
```

The settings-path hint appears only under the desktop profile.

### 5. Banner and browser

After the server binds, the profile prints:

```
Oath Digital <version> is running.
  Players open:  <public base URL>
  Data folder:   <database directory>
  Settings:      <settings file>
Seat links contain this address. If it changes, players need the new address.
Close this window or press Ctrl-C to stop.
```

The no-LAN variant replaces the first detail line with
`Only this computer can connect: http://127.0.0.1:<port>` and adds
`No local network address found. Set OATH_PUBLIC_BASE_URL in the settings file.`

The browser opens the **public base URL**, not a loopback address. The
same-origin check compares the request `Origin` with the public base URL, so a
game created from a loopback page would be rejected. In the no-LAN variant the
public base URL is absent and the browser opens `http://127.0.0.1:<port>/`.

Opening uses `open` on macOS, `cmd /c start "" <url>` on Windows, and
`xdg-open` elsewhere. Failure is logged as a warning and ignored.
`OATH_OPEN_BROWSER=false` disables it.

Closing the window or pressing Ctrl-C stops the server through the existing
graceful-shutdown hook.

### 6. Release workflow

A new `bundled` job in `.github/workflows/alpha-release.yml`:

- Depends on `archives` and downloads its staged application.
- Runs a matrix over `macos-14` (`macos-arm64`), `windows-latest`
  (`windows-x64`), and `ubuntu-24.04` (`linux-x64`).
- Installs Temurin 21 with a pinned setup action.
- Runs the bundling script, extracts the result, runs the bundled smoke, and
  uploads the archive and its checksum as a job artifact.

`publish` additionally requires the `bundled` job. The GitHub prerelease
attaches the three bundled archives, and `SHA256SUMS` covers them.

`scripts/verify-alpha-release.sh` gains a `bundled` mode that builds and
smokes the host platform's target locally.

## Error handling

- Expected failures print one plain line and exit with status 2, with no
  stack trace. These include a bad settings value, an unknown settings key,
  an unreadable settings file, a port in use, and a database location that
  cannot be created.
- Failure to write the settings template or to open the browser is a
  warning, and the server continues.
- The Start files pause on non-zero exit so the host can read the error.

## Testing

### Unit tests (munit, no real network or process calls)

- Precedence: one test per layer, including flag-without-environment.
- Settings parsing: valid file, unknown key, bad value, unreadable file.
- Template: expected content, all keys commented, never overwrites.
- App-data folder per OS name and environment, including the XDG fallback.
- LAN chooser: default-route private address, Docker or VPN interface skipped
  in favor of the probe, public probe result rejected, fallback to the first
  private interface, loopback fallback.
- Detection skipped when `OATH_PUBLIC_BASE_URL` is set, or when `OATH_HOST`
  is set to a loopback or a specific non-loopback address.
- Banner text for both variants.
- Browser command per OS, and `OATH_OPEN_BROWSER=false`.
- Port-in-use message, using a real socket held on an ephemeral port.
- Regression: without `OATH_LAUNCH`, `ServerConfig.parse` receives exactly the
  process environment.

### Bundled smoke

`scripts/smoke-bundled-distribution.sh`, with a PowerShell counterpart for
Windows. It runs the extracted archive's `bin/oathdigital` with no
`JAVA_HOME`, no Java on `PATH`, `OATH_LAUNCH=desktop`,
`OATH_OPEN_BROWSER=false`, and `HOME`, `LOCALAPPDATA`, and `XDG_DATA_HOME`
pointing at a temporary directory. It asserts:

- The Java process runs from the archive's `jre/`.
- The settings template exists in the temporary app-data folder.
- The database was created under `<app-data>/data/`.
- `/health/ready`, `/`, and `main.js` respond successfully.
- The banner's address matches the listening address.
- After a stop signal, the log contains `Oath Digital database closed`.

### Manual acceptance

Add a per-OS section to `docs/operations/alpha-acceptance.md` for the bundled
archives:

- Download with a browser and extract with the OS's own tool.
- Record the exact Gatekeeper or SmartScreen steps needed.
- Record the firewall prompt and the choice made.
- Double-click Start; confirm the banner and the browser page.
- Create a game and join it from a second machine using the banner address.

## Documentation

- `docs/operations/quick-start.md` leads with the bundled flow: download for
  your OS, extract, double-click Start, allow the firewall prompt on private
  networks only, share the printed address. The existing environment-variable
  and OCI instructions move under an "Advanced" heading.
- `docs/operations/configuration.md` documents the desktop profile, the
  app-data folders, the settings file keys, and the precedence order.
- `docs/operations/releases.md` lists the bundled archives and the new job.

## Open risks to resolve early in the plan

- **macOS quarantine.** Confirm whether a browser-downloaded, Archive
  Utility-extracted bundle runs its jlinked `java` after the one-time
  "Open Anyway" step on the Start file, or whether ad-hoc signing is needed.
- **Windows batch.** Confirm the `BUNDLED_JVM` define works when the extracted
  path contains spaces.
- **Runner availability.** Confirm `macos-14` and `windows-latest` runners are
  available to the repository once it has a GitHub remote.

## Out of scope

Intel macOS and Linux arm64 bundled archives, code signing and notarization,
native installers, a tray or menu-bar app, automatic port changes, automatic
firewall changes, and any change to command-line or OCI behavior.
