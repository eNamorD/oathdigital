#!/bin/sh
# Local verification and CI share these gates. This script never publishes.
set -eu

fail() { echo "alpha release: $*" >&2; exit 1; }

validate_tag() {
  [ "${#1}" -le 100 ] || fail "release tag is too long"
  # Reject newlines separately: grep matches lines, not the entire input.
  case "$1" in *'
'*) fail "release tag contains a newline" ;; esac
  printf '%s\n' "$1" | LC_ALL=C grep -Eq '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-(alpha|beta|rc)\.(0|[1-9][0-9]*)$' ||
    fail "expected vX.Y.Z-(alpha|beta|rc).N without leading zeros"
  printf '%s\n' "${1#v}"
}

check_tag() {
  validate_tag "$1" >/dev/null
  tag_commit=$(git rev-parse --verify "refs/tags/$1^{commit}") || fail "tag does not exist"
  [ "$tag_commit" = "$(git rev-parse HEAD)" ] || fail "HEAD does not match release tag"
}

case "${1:-}" in
  validate-tag)
    [ "$#" -eq 2 ] || fail "usage: validate-tag TAG"
    validate_tag "$2"
    ;;
  check-tag)
    [ "$#" -eq 2 ] || fail "usage: check-tag TAG"
    check_tag "$2"
    ;;
  self-test)
    for tag in v0.1.0-alpha.1 v1.2.3-beta.0 v10.20.30-rc.12; do
      validate_tag "$tag" >/dev/null
    done
    for tag in '' v1.2.3 1.2.3-alpha.1 v01.2.3-alpha.1 v1.2.3-alpha.01 \
        v1.2.3-SNAPSHOT v1.2.3-alpha.1+build '../alpha' 'v1.2.3-alpha.1
'; do
      if (validate_tag "$tag") >/dev/null 2>&1; then fail "accepted invalid tag"; fi
    done
    script=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/$(basename -- "$0")
    fixture=$(mktemp -d "${TMPDIR:-/tmp}/oathdigital-tag-test.XXXXXX")
    trap 'rm -rf -- "$fixture"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    (
      cd "$fixture"
      git init -q
      git -c user.name=ReleaseTest -c user.email=release-test@example.invalid -c commit.gpgsign=false commit -qm initial --allow-empty
      git tag v0.1.0-alpha.1
      git -c user.name=ReleaseTest -c user.email=release-test@example.invalid -c tag.gpgsign=false tag -am test v0.1.0-beta.1
      sh "$script" check-tag v0.1.0-alpha.1
      sh "$script" check-tag v0.1.0-beta.1
      if sh "$script" check-tag v0.1.0-rc.1 >/dev/null 2>&1; then fail "accepted missing tag"; fi
      git -c user.name=ReleaseTest -c user.email=release-test@example.invalid -c commit.gpgsign=false commit -qm next --allow-empty
      if sh "$script" check-tag v0.1.0-alpha.1 >/dev/null 2>&1; then fail "accepted wrong HEAD"; fi
    )
    echo "alpha release validation passed: valid/invalid versions, lightweight/annotated tags, missing tag, wrong HEAD"
    ;;
  archives)
    [ "$#" -eq 3 ] || fail "usage: archives TAG NEW_OUTPUT_DIRECTORY"
    release_tag=$2
    release_version=$(validate_tag "$release_tag")
    check_tag "$release_tag"
    git diff --quiet && git diff --cached --quiet || fail "tracked source has uncommitted changes"
    [ ! -e "$3" ] || fail "output directory already exists"
    java_version=$(java -version 2>&1)
    printf '%s\n' "$java_version" | grep -Eq 'version "21\.' || fail "Java 21 is required"
    [ -n "${JAVA_HOME:-}" ] || fail "JAVA_HOME must select Java 21 for sbtw and smoke"
    [ "$("$JAVA_HOME/bin/java" -version 2>&1)" = "$java_version" ] || fail "JAVA_HOME and PATH select different Java runtimes"
    command -v node >/dev/null || fail "Node is required for frontend tests"
    export OATH_RELEASE_VERSION=$release_version
    sh "$0" self-test
    python3 scripts/check-architecture.py
    python3 scripts/validate-component-catalog.py
    python3 scripts/check-markdown-links.py
    ./sbtw verifyReleaseVersion test frontend/test verifyPackageMappings Universal/packageBin Universal/packageZipTarball Docker/stage
    mkdir -p "$3"
    output=$(cd "$3" && pwd -P)
    temporary=$(mktemp -d "${TMPDIR:-/tmp}/oathdigital-release.XXXXXX")
    trap 'rm -rf -- "$temporary"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    root=oathdigital-$release_version
    for extension in zip tgz; do
      archive=target/universal/$root.$extension
      [ -f "$archive" ] || fail "missing archive: $archive"
      mkdir "$temporary/$extension"
      if [ "$extension" = zip ]; then
        unzip -q "$archive" -d "$temporary/$extension"
      else
        tar -xzf "$archive" -C "$temporary/$extension"
      fi
      [ "$(ls -A "$temporary/$extension")" = "$root" ] || fail "archive must have exactly one versioned root"
      sh scripts/smoke-packaged-distribution.sh "$temporary/$extension/$root" 18080
      cp "$archive" "$output/"
    done
    # Only curated evidence is published: no server logs, seat links, cookies or DB files.
    {
      printf 'Tag: %s\nCommit: %s\n' "$release_tag" "$(git rev-parse HEAD)"
      printf 'Java: 21\nHost: %s\n' "$(uname -sm)"
      printf 'Passed: JVM/frontend tests; architecture/catalog/Markdown checks; version/mappings; ZIP and TGZ smoke.\n'
      printf 'Archive smoke: readiness, frontend, three private seats, command, seat restoration across restart, database close, shutdown.\n'
      printf 'Separate-machine LAN/TLS and browser acceptance: not executed by CI.\n'
    } >"$output/archive-evidence.txt"
    release_notes_source=docs/operations/release-notes/$release_version.md
    [ -f "$release_notes_source" ] ||
      fail "missing per-version release notes: $release_notes_source"
    cp "$release_notes_source" "$output/release-notes.md"
    (cd "$output" && shasum -a 256 "$root.zip" "$root.tgz" archive-evidence.txt release-notes.md >SHA256SUMS)
    echo "alpha archive release verification passed: $release_tag"
    ;;
  bundled)
    [ "$#" -eq 4 ] || fail "usage: bundled UNIVERSAL_TGZ TARGET NEW_OUTPUT_DIRECTORY"
    case "$3" in
      macos-arm64|linux-x64) ;;
      windows-x64) fail "windows-x64 is built and smoked by the release workflow only" ;;
      *) fail "unknown target: $3" ;;
    esac
    [ -f "$2" ] || fail "missing all-platform archive: $2"
    [ ! -e "$4" ] || fail "output directory already exists"
    temporary=$(mktemp -d "${TMPDIR:-/tmp}/oathdigital-bundled-release.XXXXXX")
    trap 'rm -rf -- "$temporary"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    mkdir "$temporary/universal" "$temporary/bundled"
    tar -xzf "$2" -C "$temporary/universal"
    root=$(ls -A "$temporary/universal")
    [ "$(printf '%s\n' "$root" | wc -l | tr -d ' ')" = 1 ] || fail "archive must have exactly one versioned root"
    sh scripts/package-bundled-runtime.sh "$temporary/universal/$root" "$3" "$4"
    tar -xzf "$4/$root-$3.tgz" -C "$temporary/bundled"
    sh scripts/smoke-bundled-distribution.sh "$temporary/bundled/$root" 18081
    echo "bundled archive verification passed: $root-$3"
    ;;
  *) fail "usage: $0 {validate-tag TAG|check-tag TAG|self-test|archives TAG NEW_OUTPUT_DIRECTORY|bundled UNIVERSAL_TGZ TARGET NEW_OUTPUT_DIRECTORY}" ;;
esac
