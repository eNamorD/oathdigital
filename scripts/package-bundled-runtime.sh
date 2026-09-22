#!/bin/sh
# Turns an extracted, tested all-platform archive into a bundled-runtime
# archive for one target. Never downloads anything; JAVA_HOME supplies jlink.

set -eu

fail() {
  printf 'bundled runtime packaging failed: %s\n' "$1" >&2
  exit 1
}

[ "$#" -eq 3 ] || fail "usage: $0 APP_DIRECTORY TARGET NEW_OUTPUT_DIRECTORY"
app_directory=$1
target=$2
output_directory=$3

case "$target" in
  macos-arm64|linux-x64) extension=tgz ;;
  windows-x64) extension=zip ;;
  *) fail "unknown target: $target" ;;
esac
[ -d "$app_directory/lib" ] && [ -f "$app_directory/bin/oathdigital" ] ||
  fail "not an extracted Oath Digital archive: $app_directory"
[ ! -e "$app_directory/jre" ] || fail "app directory already contains jre/"
root=$(basename "$app_directory")
case "$root" in
  oathdigital-*) ;;
  *) fail "app directory must be named oathdigital-<version>: $root" ;;
esac
[ -n "${JAVA_HOME:-}" ] || fail "JAVA_HOME must select a Java 21 JDK"
"$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq 'version "21\.' ||
  fail "JAVA_HOME must select Java 21"
[ ! -e "$output_directory" ] || fail "output directory already exists: $output_directory"

# Windows tools need native paths; Git Bash provides cygpath.
native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}
separator=:
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) separator=';' ;; esac

script_directory=$(cd "$(dirname "$0")" && pwd -P)
modules=$(grep -Ev '^[[:space:]]*(#|$)' "$script_directory/../packaging/jlink-modules.txt" |
  tr -d '\r' | paste -sd, -)

classpath=
for jar in "$app_directory"/lib/*.jar; do
  classpath="$classpath$(native_path "$jar")$separator"
done
server_jar=
for jar in "$app_directory"/lib/dev.oathdigital.*.jar; do server_jar=$jar; done
[ -f "$server_jar" ] || fail "server jar not found under $app_directory/lib"
required=$("$JAVA_HOME/bin/jdeps" --multi-release 21 --ignore-missing-deps \
  --print-module-deps -q --class-path "$classpath" "$(native_path "$server_jar")" | tr -d '\r')
missing=
for module in $(printf '%s' "$required" | tr ',' ' '); do
  case ",$modules," in
    *",$module,"*) ;;
    *) missing="$missing $module" ;;
  esac
done
[ -z "$missing" ] ||
  fail "packaging/jlink-modules.txt lacks modules reported by jdeps:$missing"

mkdir -p "$output_directory"
output=$(cd "$output_directory" && pwd -P)
work="$output/work"
mkdir "$work"
cp -R "$app_directory" "$work/$root"
"$JAVA_HOME/bin/jlink" --add-modules "$modules" --include-locales en \
  --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
  --output "$(native_path "$work/$root/jre")"
"$work/$root/jre/bin/java" -version >/dev/null 2>&1 ||
  fail "bundled runtime does not start"
if [ "$(uname -s)" = Darwin ]; then
  codesign --verify "$work/$root/jre/bin/java" ||
    fail "bundled java has an invalid code signature"
fi

archive="$root-$target.$extension"
if [ "$extension" = tgz ]; then
  tar -czf "$output/$archive" -C "$work" "$root"
else
  python=$(command -v python3 || command -v python) ||
    fail "python is required to write zip archives"
  (cd "$work" && "$python" -m zipfile -c "$(native_path "$output/$archive")" "$root")
fi
rm -rf -- "$work"
cd "$output"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$archive"
else
  shasum -a 256 "$archive"
fi
