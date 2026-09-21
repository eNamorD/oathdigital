# Task 1 report: operator documentation and packaged guidance

## Outcome

Task 1 adds host/player startup guidance, stopped-data backup and recovery,
LAN/TLS/browser policy, and an explicitly unexecuted per-build acceptance
template. All Markdown files under `docs/operations` are now included in the
shared Universal mappings consumed by Universal and OCI packaging. Roadmap
Phase 5 item 4 is complete; items 3 and 5 remain incomplete.

No gameplay or authentication behavior changed. No live database, firewall,
proxy, publication, or remote repository state was changed.

## Files

- Added `docs/operations/quick-start.md`.
- Added `docs/operations/data-policy.md`.
- Added `docs/operations/network-and-browser.md`.
- Added `docs/operations/alpha-acceptance.md`.
- Updated `docs/operations/configuration.md`.
- Updated `README.md`.
- Updated `build.sbt`.
- Updated `docs/ROADMAP.md`.
- Added this report.

## Verification

- Preflight: `git rev-parse HEAD` returned
  `4d980701ab680b77a1da21a97ec4c19a819b3b2d`; the worktree was clean on
  `feat/phase-5-trusted-seat-access`.
- Required RED: after adding the six newly required documentation destinations
  but before adding their mappings, `./sbtw verifyPackageMappings` exited 1
  with `Invalid package mappings` and named
  `packaged-smoke-test.md`, `phase-5-follow-ups.md`, `quick-start.md`,
  `data-policy.md`, `network-and-browser.md`, and `alpha-acceptance.md` as
  missing. An initial sandboxed invocation could not create the shared sbt
  cache lock; the approved rerun reached the intended mapping assertion.
- GREEN: after mapping every `docs/operations/*.md`,
  `./sbtw verifyPackageMappings` exited 0 (`[success] Total time: 1 s`).
- `python3 scripts/check-markdown-links.py` exited 0 with
  `Markdown link check passed: 47 files`.
- `git diff --check` exited 0 with no output.
- `docker version --format '{{.Server.Version}}'` exited 1 because
  `/Users/roman/.docker/run/docker.sock` does not exist. No OCI smoke or live
  container claim is made by this documentation task.
- `git remote -v` exited 0 with no output. No publication destination exists
  yet, and no publication claim is made.

Final package-mapping, Markdown-link, staged-diff, and commit checks were rerun
immediately before the commit; the handoff records their final result and SHA.

## Source review

Technical cookie and browser statements were checked against MDN's official
`Set-Cookie` reference. The NGINX example was checked against official NGINX
`access_log` and `proxy_set_header` documentation. Named-volume backup and
restore follows official Docker volume guidance. Those sources are linked next
to the relevant guidance.

## Self-review

- Startup examples match current `OATH_*` configuration, `/`, `/games`, `/s`,
  `/health/live`, and `/health/ready` routes and the current host-form fields.
- Seat guidance preserves the trusted-group bearer-link model, cookie restore,
  separate profiles for same-game seats, and original-link recovery. It adds no
  account, password, revocation, or forwarded-header identity model.
- Data commands operate on concrete directories or named volumes only. They
  require stopped data, keep the original during restore/reset, reject broad
  deletion, and require matching-release restoration.
- Proxy guidance binds the backend to loopback, uses the exact external HTTPS
  origin, disables `/s/` access logging, excludes URI arguments, referrers, and
  cookie headers from its remaining format, and clears common forwarded
  identity headers.
- Acceptance rows remain `UNEXECUTED`; no separate-machine LAN/TLS evidence was
  inferred from automated smoke coverage.
- Only roadmap item 4 changed status. Items 3 and 5 remain open because manual
  separate-machine acceptance and release publication were not performed.

## Remaining external prerequisites

- Two separate reachable machines and chosen browser/version pairs are needed
  to execute the LAN and TLS acceptance record.
- An operator-controlled domain, certificate, proxy, and reviewed firewall
  scope are needed for the HTTPS exercise.
- A reachable Docker daemon is needed for later OCI validation.
- A configured repository remote and publication destination are needed for
  later prerelease work.

## Review follow-up

Final review identified that `access_log off` affects only NGINX access logs
and cannot support a claim about NGINX error logs or upstream logging. The
network guide now states that limitation explicitly. Internet use requires a
disposable-code exercise of normal and failing `/s/` requests plus inspection
and demonstrated redaction across every access, error, upstream, and external
log destination. Acceptance row 15 now records that evidence instead of
assuming access-log configuration protects all logs.

Prose-only follow-up verification:

- `python3 scripts/check-markdown-links.py` exited 0 with
  `Markdown link check passed: 47 files`.
- `git diff --check` exited 0 with no output.
- The package-mapping build was not rerun because no mapping or packaged-file
  path changed.
