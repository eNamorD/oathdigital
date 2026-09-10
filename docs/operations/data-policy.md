# Alpha data, backup, restore, and upgrade policy

Oath Digital uses a file-backed HSQLDB database. Treat the complete directory
containing the configured database prefix as one unit. For example, when
`OATH_DATABASE_PATH=/home/alex/oathdigital-data/alpha-1/database`, the data
directory is `/home/alex/oathdigital-data/alpha-1`, not one selected
`database.*` file.

**Never copy, restore, or move a live HSQLDB data directory.** Stop Oath
Digital, wait for the process or container to exit, and confirm the
`Oath Digital database closed` log line first. A partial or live copy is not a
supported backup.

## Compatibility contract

Every alpha release must state its data compatibility with the previous
release. If release notes do not explicitly say an upgrade is compatible,
assume it is incompatible and preserve the old data with the old release.

| Build or release | Schema | Compatible input | Restore runtime |
| --- | ---: | --- | --- |
| `0.1.0-SNAPSHOT` at this source revision | 4 | Same build only; no cross-release promise | Exact same build |

Startup applies known contiguous migrations. A database whose schema is newer
than the running build supports is rejected at startup; the server does not
downgrade or reinterpret it. Never work around that rejection by editing
database files. Start the matching newer release or restore a complete backup
with its matching release.

## Archive backup and restore on macOS or Linux

Stop the server first. These commands copy one exact stopped directory to a
dated backup:

```sh
mkdir -p /home/alex/oathdigital-backups
cp -Rp /home/alex/oathdigital-data/alpha-1 \
  /home/alex/oathdigital-backups/alpha-1-2026-09-09
```

Record the application version and archive checksum next to the backup. To
restore, keep the current directory as a reversible fallback and copy the
complete backup into its place:

```sh
mv /home/alex/oathdigital-data/alpha-1 \
  /home/alex/oathdigital-data/alpha-1.before-restore-2026-09-09
cp -Rp /home/alex/oathdigital-backups/alpha-1-2026-09-09 \
  /home/alex/oathdigital-data/alpha-1
```

Start the exact release recorded with that backup and point
`OATH_DATABASE_PATH` at
`/home/alex/oathdigital-data/alpha-1/database`. Do not start a different
release against the restored directory unless its release notes explicitly
allow that input format.

## Archive backup and restore on Windows

Stop the server first. Run these commands in PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path D:\OathDigitalBackups
Copy-Item -Recurse -LiteralPath C:\OathDigitalData\alpha-1 -Destination D:\OathDigitalBackups\alpha-1-2026-09-09
```

Restore to a reversible replacement and use the matching application release:

```powershell
Move-Item -LiteralPath C:\OathDigitalData\alpha-1 -Destination C:\OathDigitalData\alpha-1.before-restore-2026-09-09
Copy-Item -Recurse -LiteralPath D:\OathDigitalBackups\alpha-1-2026-09-09 -Destination C:\OathDigitalData\alpha-1
```

## Named-volume backup and restore

Stop and remove the application container but keep its named volume:

```sh
docker stop --time 15 oathdigital
docker rm oathdigital
mkdir -p "$PWD/oathdigital-backups"
docker run --rm \
  --volume oathdigital-data:/source:ro \
  --volume "$PWD/oathdigital-backups":/backup \
  alpine:3.20 \
  tar -czf /backup/oathdigital-data-2026-09-09.tgz -C /source .
```

The helper image must be available locally or pulled from its trusted registry.
The volume is mounted read-only during backup.

Restore into a new named volume so the old one remains untouched:

```sh
docker volume create oathdigital-data-restored-2026-09-09
docker run --rm \
  --volume oathdigital-data-restored-2026-09-09:/target \
  --volume "$PWD/oathdigital-backups":/backup:ro \
  alpine:3.20 \
  tar -xzf /backup/oathdigital-data-2026-09-09.tgz -C /target
docker run --detach --name oathdigital \
  --publish 8080:8080 \
  --env OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
  --volume oathdigital-data-restored-2026-09-09:/var/lib/oathdigital \
  oathdigital:0.1.0-SNAPSHOT
```

Keep `oathdigital-data` until the restored copy has passed acceptance. Docker's
official [volume backup and restore guidance](https://docs.docker.com/engine/storage/volumes/#back-up-restore-or-migrate-data-volumes)
uses the same stopped-volume and helper-container model.

## Upgrade procedure

1. Read the incoming release's explicit compatibility statement.
2. Stop the current release cleanly.
3. Back up the complete stopped data directory or named volume.
4. Record the current release version, incoming version, backup location, and
   artifact checksum.
5. For a compatible upgrade, make a working copy of the stopped backup and
   start the incoming release against that copy. Keep the original data and old
   release unchanged until acceptance passes.
6. For an incompatible upgrade, start the incoming release with a new empty
   data location. Retain the old data with the old release.

If an upgrade fails, stop the new release. Start the old release against its
unchanged old directory or restore the pre-upgrade backup to a separate path.
Never point an older release at a directory already migrated by a newer one.

## Reversible reset

A reset means selecting a fresh data location, not deleting the old one. For an
archive on Linux or macOS:

```sh
mv /home/alex/oathdigital-data/alpha-1 \
  /home/alex/oathdigital-data/alpha-1.before-reset-2026-09-09
mkdir -p /home/alex/oathdigital-data/alpha-1
```

For Windows PowerShell:

```powershell
Move-Item -LiteralPath C:\OathDigitalData\alpha-1 -Destination C:\OathDigitalData\alpha-1.before-reset-2026-09-09
New-Item -ItemType Directory -Force -Path C:\OathDigitalData\alpha-1
```

For Docker, create a new named volume and start the next container with that
name. Keep the previous named volume. Rollback is then a stopped-container
change back to the previous path or volume; no broad deletion is required.
