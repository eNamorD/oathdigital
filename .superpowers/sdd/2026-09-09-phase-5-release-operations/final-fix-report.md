# Final operations documentation fix report

Base: `a7133ecb5b2e6b7f9e5cdb218775052f0f166cd0` on
`feat/phase-5-trusted-seat-access`.

## Scope and outcome

This documentation-only fix addresses the four consolidated final-review
findings without changing code, package mappings, workflows, tests, or recorded
release evidence.

- `quick-start.md` now uses a versioned, operator-supplied published GHCR image
  reference instead of the local `oathdigital:0.1.0-SNAPSHOT` developer image.
  The matching restore command in `data-policy.md` and the runtime example in
  `configuration.md` use the same `OATH_IMAGE_REFERENCE` convention.
- Archive examples now use the illustrative release version
  `0.1.0-alpha.1` and tell operators to replace it with the exact version in
  the downloaded archive. Published archive and OCI use requires neither sbt
  nor Node.
- `configuration.md` now uses a retained detached container. It no longer
  combines `docker run --rm` with later `docker logs`, `docker stop`, and
  `docker rm` instructions.
- `phase-5-follow-ups.md` no longer links from the flattened packaged directory
  to an unbundled `../superpowers` plan. Source-only plan and SDD report paths
  are labeled as source-checkout references, while packaged links remain among
  the flattened operations documents.
- `releases.md` records GHCR's initial private visibility and requires the
  package administrator either to make the package public or grant host read
  access and provide an authenticated pull procedure. It warns that GitHub's
  private-to-public visibility change cannot be reversed.

## Official source verification

Current GitHub documentation was checked on 2026-09-10:

- [Package access and visibility](https://docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility)
  states that a first-published package is private by default, public container
  packages can be pulled anonymously, and public conversion cannot be undone.
- [Working with the Container registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
  documents private pulls with a personal access token (classic) limited to
  `read:packages` and `docker login ghcr.io`.

## Verification

- `python3 scripts/check-markdown-links.py`: exit 0,
  `Markdown link check passed: 48 files`.
- Packaged-layout link validation copied all eight `docs/operations/*.md` files
  into one temporary flattened documentation directory and ran the repository
  link checker against that layout: exit 0,
  `Markdown link check passed: 9 files` (eight packaged documents plus the
  checker's empty root README fixture).
- `git diff --check`: exit 0 with no output.
- No full build or gameplay suite ran because no source, build definition,
  workflow, package mapping, or packaged documentation path changed.

## Self-review

- Every end-user OCI start or restore example uses the same explicit published
  reference placeholder and a version tag; local snapshot references remain
  only in developer smoke/history context.
- Archive examples distinguish the leading-`v` Git/OCI tag from archive
  filenames and extraction roots.
- Quick-start and configuration container commands now agree on pull, named
  volume, detached start, log viewing, clean stop, and explicit removal.
- Every relative Markdown link in the eight packaged operations documents
  resolves when those documents are flattened under `share/oathdigital`.
- GHCR access text does not claim an image exists or has been made public. It
  preserves the existing unexecuted publication and external-gate status.

No publication, package-visibility change, registry login, image pull,
container operation, firewall/proxy change, or database operation was
performed.
