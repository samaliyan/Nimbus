#!/usr/bin/env bash
# Downloads the Aether release binaries for Android, checks them against the release's
# SHA256SUMS.txt and places them where Gradle packages native libraries.
# Usage: tools/fetch-core.sh <tag> <jniLibs dir> <licenses dir>
set -euo pipefail

tag="$1"
jni="$2"
lic="$3"
base="https://github.com/CluvexStudio/Aether/releases/download/$tag"
work="$(mktemp -d)"

curl -fsSL --retry 3 -o "$work/SHA256SUMS.txt" "$base/SHA256SUMS.txt"

declare -A abis=( [arm64-v8a]=arm64 [armeabi-v7a]=armv7 [x86_64]=x86_64 )
for abi in "${!abis[@]}"; do
  archive="aether-android-${abis[$abi]}.tar.gz"
  curl -fsSL --retry 3 -o "$work/$archive" "$base/$archive"
  expected="$(awk -v f="$archive" '$2 == f { print $1 }' "$work/SHA256SUMS.txt")"
  actual="$(sha256sum "$work/$archive" | cut -d' ' -f1)"
  if [[ -z "$expected" || "$expected" != "$actual" ]]; then
    echo "checksum mismatch for $archive: expected '$expected', got '$actual'" >&2
    exit 1
  fi
  mkdir -p "$work/$abi" "$jni/$abi"
  tar -xzf "$work/$archive" -C "$work/$abi"
  install -m 0755 "$work/$abi/aether" "$jni/$abi/libaether.so"
  install -m 0755 "$work/$abi/pt/psiphon-tunnel-core" "$jni/$abi/libpsiphon.so"
  install -m 0755 "$work/$abi/pt/lyrebird" "$jni/$abi/liblyrebird.so"
  echo "$abi: aether $(sha256sum "$jni/$abi/libaether.so" | cut -c1-16)…"
done

mkdir -p "$lic"
cp -R "$work/arm64-v8a/LICENSES/." "$lic/"
rm -rf "$work"
