# Trusted-alpha quick start

Oath Digital's trusted-alpha mode is for a group whose members trust one
another. Each seat link grants full control of that seat. There are no
accounts, passwords, invitations, revocation, or remote-administration tools.
Send each link only to its intended player and keep a private saved copy.

## Universal archive

The ZIP and TGZ archives require Java 21. They do not require sbt or Node.
Verify the runtime before starting:

```sh
java -version
```

Confirm the reported major version is 21.

Use a data directory outside the extracted application so replacing the
application does not replace the database. The value of `OATH_DATABASE_PATH`
is the database file prefix inside that directory.

### macOS

This example assumes `oathdigital-0.1.0-SNAPSHOT.tgz` is in Downloads and the
host's LAN address is `192.168.1.20`:

```sh
mkdir -p /Users/alex/Applications
cd /Users/alex/Applications
tar -xzf /Users/alex/Downloads/oathdigital-0.1.0-SNAPSHOT.tgz
mkdir -p /Users/alex/OathDigitalData/alpha-1
cd oathdigital-0.1.0-SNAPSHOT
OATH_HOST=0.0.0.0 \
OATH_PORT=8080 \
OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
OATH_DATABASE_PATH=/Users/alex/OathDigitalData/alpha-1/database \
bin/oathdigital
```

### Linux

This example assumes the archive is in `/home/alex/Downloads`:

```sh
mkdir -p /home/alex/apps
cd /home/alex/apps
tar -xzf /home/alex/Downloads/oathdigital-0.1.0-SNAPSHOT.tgz
mkdir -p /home/alex/oathdigital-data/alpha-1
cd oathdigital-0.1.0-SNAPSHOT
OATH_HOST=0.0.0.0 \
OATH_PORT=8080 \
OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
OATH_DATABASE_PATH=/home/alex/oathdigital-data/alpha-1/database \
bin/oathdigital
```

### Windows

Run these commands in PowerShell. This example assumes the ZIP is in
`C:\Users\Alex\Downloads`:

```powershell
New-Item -ItemType Directory -Force -Path C:\OathDigital
Set-Location C:\OathDigital
Expand-Archive -LiteralPath C:\Users\Alex\Downloads\oathdigital-0.1.0-SNAPSHOT.zip -DestinationPath .
New-Item -ItemType Directory -Force -Path C:\OathDigitalData\alpha-1
$env:OATH_HOST = '0.0.0.0'
$env:OATH_PORT = '8080'
$env:OATH_PUBLIC_BASE_URL = 'http://192.168.1.20:8080'
$env:OATH_DATABASE_PATH = 'C:\OathDigitalData\alpha-1\database'
.\oathdigital-0.1.0-SNAPSHOT\bin\oathdigital.bat
```

Replace `192.168.1.20` with the host's private LAN address. For host-only use,
omit `OATH_HOST` and set or omit the public base URL as described in
[runtime configuration](configuration.md). For Internet exposure, do not use
these direct HTTP examples; use the HTTPS arrangement in
[network and browser guidance](network-and-browser.md).

Press Ctrl-C once to stop an archive process. Wait for the process to exit and
the `Oath Digital database closed` log line before backing up or moving data.

## OCI image

The image includes Java 21 and runs as UID 10001. Use a named volume so data
survives container removal:

```sh
docker volume create oathdigital-data
docker run --detach --name oathdigital \
  --publish 8080:8080 \
  --env OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
  --volume oathdigital-data:/var/lib/oathdigital \
  oathdigital:0.1.0-SNAPSHOT
docker logs --follow oathdigital
```

Stop cleanly before backup, upgrade, or restore, then remove only the stopped
container. The named volume remains:

```sh
docker stop --time 15 oathdigital
docker rm oathdigital
docker volume inspect oathdigital-data
```

See [data and upgrade policy](data-policy.md) before copying or changing a
database.

## Create and distribute seats

1. Open the configured public base URL, such as
   `http://192.168.1.20:8080/`, on the host.
2. In **Game ID**, enter a unique game identifier.
3. In **Seat definitions**, enter one line per player in the exact form
   `player ID,lineage ID,color`. Available colors are `red`, `blue`, `yellow`,
   `white`, and `black`.
4. In **First player ID**, enter one of those player IDs, then select
   **Create game**.
5. Copy and privately save every displayed seat link before leaving the page.
   The creation result is the only place that lists all raw links.
6. Send each player only their assigned link. Do not post links in public chat,
   issue trackers, screenshots, or access logs.

Opening `/s/{seat-code}` stores a seat cookie and redirects to the canonical
`/games/{game-id}` page. Reloading that page restores the same private seat.
The server remains authoritative after disconnects and reloads.

Each player should bookmark their original assigned `/s/` link on a private
device. If cookies are cleared, open that original link again to restore the
seat. The same link can initialize a replacement browser or device, and anyone
who obtains it can control the seat.

Use separate browser profiles for different seats in the same game. All seats
use the same cookie name and game-scoped path, so opening two seat links for one
game in one browser profile replaces the profile's current seat. A private or
incognito window is suitable only if losing its cookies at window close is
acceptable and the original link is still available.

## Before players join

- Confirm `GET /health/live` and `GET /health/ready` both return success.
- Confirm every player can reach the exact public base URL from their machine.
- Keep the database directory or named volume on persistent storage.
- Record the build and run the [alpha acceptance record](alpha-acceptance.md).
