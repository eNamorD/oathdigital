# Alpha releases

This workflow is prepared for GitHub; local implementation is not publication
evidence. No repository remote or registry owner is assumed. The GitHub Actions
run derives its image name from the current repository:
`ghcr.io/<owner>/<repository>:v0.1.0-alpha.1` (lowercase owner and repository).
The tag here is an example, not a claim that this release exists.

## GHCR access for hosts

GitHub Container Registry packages are private when first published. After the
first successful publication, a package administrator must choose one of these
host-access models before distributing the OCI quick start:

- Make the package public so hosts can pull it anonymously. GitHub warns that
  changing a package from private to public cannot be undone.
- Keep the package private, grant each host operator read access, and have that
  operator authenticate to `ghcr.io` with a personal access token (classic)
  limited to `read:packages` before pulling:

  ```sh
  printf '%s' "$CR_PAT" | docker login ghcr.io --username '<github-user>' --password-stdin
  docker pull 'ghcr.io/<owner>/<repository>:v0.1.0-alpha.1'
  docker logout ghcr.io
  ```

  Set `CR_PAT` through an approved secret mechanism rather than placing its
  value in shell history. Replace all placeholders with the access grant and
  exact versioned image reference supplied by the release operator.

GitHub documents initial private visibility, irreversible public conversion,
and anonymous access for public container packages in
[package access and visibility](https://docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility).
Its [Container registry guide](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
documents private pulls with a classic token scoped to `read:packages`.

## Versions and compatibility

Release tags must be `vX.Y.Z-alpha.N`, `vX.Y.Z-beta.N`, or `vX.Y.Z-rc.N`, with
nonnegative integers and no leading zeros. Stable versions, `SNAPSHOT`, build
metadata, branches, and arbitrary refs are rejected. Local builds retain
`0.1.0-SNAPSHOT`. Set `OATH_RELEASE_VERSION=0.1.0-alpha.1` to build a prerelease
locally; the `v` belongs only to the Git/registry tag. Both archive filenames and
their single extraction directory are `oathdigital-0.1.0-alpha.1`, with `.zip`
or `.tgz` on the filename. Two versions can be extracted side by side.

Universal archives require Java 21; OCI images include Java 21 and run as UID
10001. The workflow verifies Linux `amd64` and `arm64`. macOS and Windows
launchers are included, but their browser/OS acceptance is provisional until
recorded in the [alpha acceptance record](alpha-acceptance.md).

No compatibility with another release's database is promised for this initial
alpha. Restore only with the exact release that created the backup. Before any
upgrade, stop the server and back up the complete database directory as described
in the [data policy](data-policy.md). For subsequent tags, update these release
notes with the exact supported source versions, migration behavior, and rollback
limits before tagging. Schema checks do not replace release-specific policy.

## Operator sequence

1. Connect the reviewed repository to GitHub. Put the workflow on its default
   branch, enable Actions and GHCR package publication, and restrict creation,
   movement, and deletion of release tags to trusted operators. A workflow
   dispatch executes repository code: only dispatch reviewed tags and reviewed
   workflow revisions. The publisher needs `contents: write` and `packages:
   write`; verification jobs have only `contents: read`.
2. Complete the manual [LAN/TLS acceptance record](alpha-acceptance.md) on the
   intended build and add release-specific compatibility notes. Create and push
   the chosen prerelease tag through your normal reviewed release process.
   This implementation does not create or push a tag.
3. In Actions, select **Alpha release**, enter the exact existing tag, and leave
   **publish** false. The workflow checks out that tag, verifies its commit,
   runs JVM/frontend tests and architecture/catalog/Markdown/version/mapping
   checks, extracts and smokes both archives under Java 21, and smokes loaded
   `linux/amd64` and `linux/arm64` images. Download and inspect the resulting
   artifacts. Verification does not write to GHCR or create a GitHub release.
4. Once satisfied with that evidence and manual acceptance, dispatch the same
   tag with **publish** true. This new run repeats every gate. Publication can
   start only when the archive job and both image jobs pass in that run. It
   reloads the tested image archives and checks their saved image IDs; it does
   not rebuild them. Only the version tag and its `-amd64`/`-arm64` child tags
   are published. There is no `latest` tag.
5. After the first successful GHCR publication, configure public or private
   host access as described above. Confirm a clean host can pull the exact
   published reference before distributing OCI startup instructions.

The GitHub prerelease contains both Universal archives, `SHA256SUMS`, these
release notes, and sanitized archive/image/manifest evidence. Checksums cover
the archives and evidence files. Verify downloads with `sha256sum --check
SHA256SUMS` on Linux or `shasum -a 256 --check SHA256SUMS` on macOS. Successful
smoke evidence contains only source/version/platform, image identity, and test
summaries; temporary databases, seat links, cookies, and raw server logs are not
uploaded. CI cannot attest to separate-machine LAN/TLS or browser acceptance.

## Failure and retry policy

Same-tag runs are serialized. Before any publication writes, the workflow
refuses an existing GitHub release or any of the three destination image tags,
and rejects a Git tag moved since verification. Unexpected API/registry errors
also stop publication; a failed lookup is not treated as proof of absence.
Tags are immutable by this workflow's policy. GHCR tags can still be changed by
other authorized writers, so restrict those writers and protect Git tags.

Publishing images, a manifest, and a GitHub release is not atomic. If publication
fails partway, some image tags may exist without a GitHub release. A retry then
stops at the existing-tag check. Preserve the run's evidence and published
digests; inspect the failure and use a new prerelease tag after correction.
Do not delete or overwrite published versions to make a retry pass. No cleanup
or remote repair is automated. GitHub Actions artifacts expire after 14 days;
retain evidence outside Actions if investigation needs longer.

## Local verification

With Java 21 selected in both `JAVA_HOME` and `PATH`, Node on `PATH`, and either
the repository's `.tooling/sbt` or a system `sbt`, run from the exact tagged
checkout:

```sh
sh scripts/verify-alpha-release.sh self-test
sh scripts/verify-alpha-release.sh check-tag v0.1.0-alpha.1
sh scripts/verify-alpha-release.sh archives v0.1.0-alpha.1 /tmp/oath-alpha-verification
```

The output directory must not exist. The verifier never publishes. `sbtw`
honors `JAVA_HOME`, otherwise retains the existing bundled macOS JDK 17 for
ordinary development; release verification explicitly requires Java 21.
Local `./sbtw Docker/publishLocal` still builds only the host architecture.
The workflow stages the same JVM output for Buildx, then loads and smokes each
platform before saving it as a tested-image artifact.

## Workflow sources

Action revisions are pinned to full commits, verified against official upstream
tags on 2026-09-09. Syntax and behavior follow the official
[GitHub workflow reference](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax),
[Docker platform workflow guide](https://docs.docker.com/build/ci/github-actions/multi-platform/),
[Docker image loading guidance](https://docs.docker.com/build/building/multi-platform/),
[manifest assembly reference](https://docs.docker.com/reference/cli/docker/buildx/imagetools/create/),
and [GitHub prerelease CLI](https://cli.github.com/manual/gh_release_create).
