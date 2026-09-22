# Trusted-alpha quick start

Oath Digital's trusted-alpha mode is for a group whose members trust one
another. Each seat link grants full control of that seat. There are no
accounts, passwords, invitations, revocation, or remote-administration tools.
Send each link only to its intended player and keep a private saved copy.

## Easiest: download for your computer

Download the archive for your computer. It includes its own Java, so nothing
else needs installing:

| Computer | Download |
| --- | --- |
| Mac with Apple silicon | `oathdigital-0.1.0-alpha.1-macos-arm64.tgz` |
| Windows (64-bit Intel or AMD) | `oathdigital-0.1.0-alpha.1-windows-x64.zip` |
| Linux (64-bit Intel or AMD) | `oathdigital-0.1.0-alpha.1-linux-x64.tgz` |

1. Extract the archive: double-click it on macOS, or right-click and choose
   **Extract All** on Windows. On Linux, extract it with your file manager.
2. Move the extracted `oathdigital-0.1.0-alpha.1` folder out of Downloads (or
   Desktop or Documents) into a plain folder such as your home folder or
   `Applications`, for example by dragging it there in Finder or Explorer.
   Starting the server from inside Downloads, Desktop, or Documents makes the
   operating system ask for extra one-time folder-access permission; starting
   it from elsewhere avoids that prompt.
3. Open the moved folder and start the server:
   - macOS: double-click **Start Oath Digital.command**. The first time, macOS
     says it cannot verify the file. Open **System Settings › Privacy &
     Security**, choose **Open Anyway** for it, and confirm.
   - Windows: double-click **Start Oath Digital.bat**. If SmartScreen appears,
     choose **More info › Run anyway**.
   - Linux: run `./start-oathdigital.sh` from a terminal in that folder, or use
     your file manager's "Run in terminal" action.
4. If the system asks whether to allow incoming connections, allow them on
   private networks only.
5. The window shows the address players open, for example
   `http://192.168.1.20:8080`, and your browser opens the game-creation page.
   Continue with [Create and distribute seats](#create-and-distribute-seats).
6. Keep the window open while you play. Close it, or press Ctrl-C, to stop.

Your games are stored outside the extracted folder, so you can replace the
folder with a newer version. The window shows the data folder and the settings
file. See the [desktop launch profile](configuration.md#desktop-launch-profile)
to change the port or the address, or to turn off the browser opening.

Seat links contain the address. If the host's network address changes, for
example after joining a different network, players need the new address.

## Advanced: all-platform archive and OCI image

The sections below need a terminal. Use them for other computers, servers, or
HTTPS arrangements.

### Universal archive

The ZIP and TGZ archives require Java 21. They do not require sbt or Node.
The same archives also contain the Start files, which use the installed
Java 21. Verify the runtime before starting:

```sh
java -version
```

Confirm the reported major version is 21.

Use a data directory outside the extracted application so replacing the
application does not replace the database. The value of `OATH_DATABASE_PATH`
is the database file prefix inside that directory.

#### macOS

These archive examples use version `0.1.0-alpha.1`. Replace that version in the
filename and extracted directory with the exact version in the archive you
downloaded. A Git or OCI tag has a leading `v`; the archive filename and
directory do not. This macOS example also assumes the host's LAN address is
`192.168.1.20`:

```sh
mkdir -p /Users/alex/Applications
cd /Users/alex/Applications
tar -xzf /Users/alex/Downloads/oathdigital-0.1.0-alpha.1.tgz
mkdir -p /Users/alex/OathDigitalData/alpha-1
cd oathdigital-0.1.0-alpha.1
OATH_HOST=0.0.0.0 \
OATH_PORT=8080 \
OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
OATH_DATABASE_PATH=/Users/alex/OathDigitalData/alpha-1/database \
bin/oathdigital
```

#### Linux

This example assumes the archive is in `/home/alex/Downloads`:

```sh
mkdir -p /home/alex/apps
cd /home/alex/apps
tar -xzf /home/alex/Downloads/oathdigital-0.1.0-alpha.1.tgz
mkdir -p /home/alex/oathdigital-data/alpha-1
cd oathdigital-0.1.0-alpha.1
OATH_HOST=0.0.0.0 \
OATH_PORT=8080 \
OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
OATH_DATABASE_PATH=/home/alex/oathdigital-data/alpha-1/database \
bin/oathdigital
```

#### Windows

Run these commands in PowerShell. This example assumes the ZIP is in
`C:\Users\Alex\Downloads`:

```powershell
New-Item -ItemType Directory -Force -Path C:\OathDigital
Set-Location C:\OathDigital
Expand-Archive -LiteralPath C:\Users\Alex\Downloads\oathdigital-0.1.0-alpha.1.zip -DestinationPath .
New-Item -ItemType Directory -Force -Path C:\OathDigitalData\alpha-1
$env:OATH_HOST = '0.0.0.0'
$env:OATH_PORT = '8080'
$env:OATH_PUBLIC_BASE_URL = 'http://192.168.1.20:8080'
$env:OATH_DATABASE_PATH = 'C:\OathDigitalData\alpha-1\database'
.\oathdigital-0.1.0-alpha.1\bin\oathdigital.bat
```

Replace `192.168.1.20` with the host's private LAN address. For host-only use,
omit `OATH_HOST` and set or omit the public base URL as described in
[runtime configuration](configuration.md). For Internet exposure, do not use
these direct HTTP examples; use the HTTPS arrangement in
[network and browser guidance](network-and-browser.md).

Press Ctrl-C once to stop an archive process. Wait for the process to exit and
the `Oath Digital database closed` log line before backing up or moving data.

### OCI image

The image includes Java 21 and runs as UID 10001. Ask the release operator for
the exact published, versioned OCI reference; do not substitute an unversioned
or `latest` tag. Set that reference before pulling and starting the image. The
placeholder below must be replaced with the actual lowercase GitHub owner and
repository:

```sh
export OATH_IMAGE_REFERENCE='ghcr.io/<owner>/<repository>:v0.1.0-alpha.1'
docker pull "$OATH_IMAGE_REFERENCE"
docker volume create oathdigital-data
docker run --detach --name oathdigital \
  --publish 8080:8080 \
  --env OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
  --volume oathdigital-data:/var/lib/oathdigital \
  "$OATH_IMAGE_REFERENCE"
docker logs --follow oathdigital
```

The first GHCR publication is private by default. Before giving this command to
a host, the release operator must either make the package public for anonymous
pulls or grant that host read access and provide an authorized `docker login`
procedure. See [alpha releases](releases.md#ghcr-access-for-hosts). Running a
published image requires neither sbt nor Node.

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
2. The page starts with a red and a blue player. Use **Add a Player** to add
   another color (`red`, `blue`, `yellow`, `white`, `black`, `pink`, or
   `brown`), up to six players, and **Remove** to drop one. A game needs at
   least two players. Each color is its own lineage.
3. Optionally edit each player ID. IDs default to the color name, must be
   unique, and may use letters, digits, `.`, `_`, `:` and `-`.
4. Select **Create game**. The page generates the game ID. The seating order
   and the first player are shuffled when the game starts, along with the sites
   and decks.
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
