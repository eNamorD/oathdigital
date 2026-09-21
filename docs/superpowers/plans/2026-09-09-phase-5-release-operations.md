# Phase 5 Release Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Package complete operator guidance, provide repeatable LAN/TLS acceptance, and prepare a gated GitHub prerelease workflow for Universal archives and both Linux OCI architectures.

**Architecture:** Keep the existing Universal and Docker mappings as the runtime source of truth. Bundle operator documentation through those mappings. CI verifies the same staged distributions and image smoke scripts before publishing immutable version tags; external release publication remains a separate operator action.

**Tech Stack:** Existing sbt wrapper and sbt-native-packager, POSIX shell, GitHub Actions, Docker Buildx, Markdown.

**Spec:** `docs/superpowers/specs/2026-09-07-phase-5-alpha-readiness-design.md`

## Global Constraints

- Work in `feat/phase-5-trusted-seat-access`; preserve gameplay and existing development/authenticated routes.
- Universal archives require Java 21. Linux OCI images include Java 21 and run as UID 10001.
- Test both `linux/amd64` and `linux/arm64` before publication.
- Seat links grant control and are for trusted groups. Do not introduce accounts or remote administration.
- Never copy a live HSQLDB directory. Backup and restore operate on the complete stopped database directory.
- Never claim manual separate-machine LAN/TLS acceptance or publication without observed evidence.
- Do not push, publish, change firewall rules, or alter existing user databases during implementation.

### Task 1: Operator documentation and packaged guidance

**Files:** Create `docs/operations/quick-start.md`, `docs/operations/data-policy.md`, `docs/operations/network-and-browser.md`, and `docs/operations/alpha-acceptance.md`. Update `docs/operations/configuration.md`, `README.md`, `build.sbt`, and `docs/ROADMAP.md`.

- [ ] Document archive startup on macOS/Linux/Windows with writable external data directory and Java 21, plus named-volume Docker startup and shutdown.
- [ ] Explain host creation form, manual seat-link distribution, cookie restoration, separate browser profiles for different seats in one game, stable original-link bookmarks, and recovery after cookies are cleared.
- [ ] Document stopped-directory backup, matching-release restore, backup-before-upgrade, newer-schema rejection, explicit per-release compatibility, and reversible reset by moving the stopped data directory aside. Include runnable commands with concrete example paths; never recommend broad deletion.
- [ ] Document browser support as provisional unless tested; LAN private address, firewall scope, HTTPS proxy origin, direct-backend restrictions, and proxy access-log redaction for `/s/` and Cookie headers. Include reverse-proxy configuration with access logging disabled for credential exchange and no trust in forwarded identity headers.
- [ ] Provide a per-build acceptance record template for two separate machines: host creation, three seat links, representative multiplayer turns, disconnect/reconnect, same-database restart, correct private views, and HTTPS cookie/origin behavior. Mark unexecuted rows explicitly.
- [ ] Bundle every operations Markdown file in Universal and OCI mappings. Extend `verifyPackageMappings` required documentation paths, first observe failure for missing mappings, then add mappings and pass `./sbtw verifyPackageMappings`.
- [ ] Run Markdown link checker and `git diff --check`; mark roadmap Item 4 complete only after guidance exists. Leave Item 3 open without separate-machine evidence.
- [ ] Commit `docs: add alpha hosting and data recovery guidance`.

### Task 2: Versioned release workflow

**Files:** Create `.github/workflows/alpha-release.yml`, `scripts/verify-alpha-release.sh`, `docs/operations/releases.md`; update `build.sbt` and configuration/follow-up documentation where claims change.

- [ ] Inspect current official GitHub Actions and Docker documentation before choosing workflow syntax/action revisions. Use pinned action revisions and least job permissions.
- [ ] Add validated version override for CI without changing default snapshot version. Ensure archives extract to a versioned directory without duplicate version suffixes.
- [ ] Provide manual workflow dispatch with an explicit prerelease tag and publishing disabled by default. Check out that exact tag and reject non-prerelease or malformed versions before building. Do not invent a repository remote or registry owner.
- [ ] Configure Java and Node tooling for the existing `./sbtw` wrapper, preserving local `.tooling` behavior. Run JVM/frontend tests, architecture/catalog/link checks applicable to the repository, package mapping checks, and Universal smoke before uploads.
- [ ] Build and load each Linux architecture image using Docker Buildx with QEMU where necessary; run the existing container smoke against each loaded image before a publication job can execute.
- [ ] Gate publication on both architecture jobs and archive verification. Publish a version-tagged multiarchitecture manifest to the current repository's GHCR namespace and a GitHub prerelease containing archives, checksums, release notes, and sanitized smoke evidence. Do not publish `latest` or overwrite an existing release/tag.
- [ ] Verify shell syntax, workflow structure, version validation failure paths, generated mappings, and staged Dockerfile. Record unrun remote jobs accurately.
- [ ] Commit `build: prepare gated multiarchitecture alpha releases`.

### Task 3: Final release readiness evidence

**Files:** Update `docs/operations/alpha-acceptance.md`, `docs/operations/phase-5-follow-ups.md`, `docs/ROADMAP.md`, and this plan.

- [x] Reused Task 2's exact unchanged-code Java 21 evidence instead of repeating
  broad suites: `./sbtw verifyReleaseVersion test frontend/test
  verifyPackageMappings Universal/packageBin Universal/packageZipTarball
  Docker/stage` exited 0 on macOS arm64 with Temurin `21.0.12.1+1-LTS`,
  `OATH_RELEASE_VERSION=0.1.0-alpha.1`, 563 JVM tests, 137 frontend tests, and
  two separately extracted Universal smokes. Source:
  `.superpowers/sdd/2026-09-09-phase-5-release-operations/task-2-report.md`,
  commit `5b817f6`.
- [x] Inspected Docker read-only on 2026-09-10. `docker info --format
  '{{.ServerVersion}} {{.Architecture}}'` reported that
  `/Users/roman/.docker/run/docker.sock` does not exist; no live container
  checks could run. Docker socket access was not denied, so no escalation was
  requested.
- [x] Recorded exact automated evidence, version, architecture, and external
  prerequisites in the acceptance record, follow-ups, and roadmap. Item 4 is
  complete; Items 3 and 5 remain unchecked pending observed manual or remote
  evidence.
- [x] Reviewed operations changes against approved alpha-readiness design and
  Task 2 report. No actionable documentation or workflow finding remains in
  this unchanged-code evidence pass; final Markdown-link and whitespace checks
  are recorded with the final-evidence commit.
