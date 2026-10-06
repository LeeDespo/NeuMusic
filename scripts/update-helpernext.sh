#!/bin/sh
# Supply app/helpernext/ from the official HelperNext Release pinned in
# app/helpernext.lock.json: download -> sha256 verify -> verified atomic swap.
# No component source checkout, no Rust/NDK/boltffi toolchain is involved.
set -eu
app_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
lock="$app_root/app/helpernext.lock.json"

read_lock() {
    python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$lock" "$1"
}

for field in repository component version tag asset gitCommit sha256; do
    value=$(read_lock "$field") || { echo "helpernext.lock.json: missing field '$field'" >&2; exit 1; }
    [ -n "$value" ] || { echo "helpernext.lock.json: empty field '$field'" >&2; exit 1; }
done
repository=$(read_lock repository)
version=$(read_lock version)
tag=$(read_lock tag)
asset=$(read_lock asset)
expected_sha=$(read_lock sha256)

work=$(mktemp -d "${TMPDIR:-/tmp}/helpernext-download.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM
zip_path="$work/$asset"
url="https://github.com/$repository/releases/download/$tag/$asset"

# GitHub direct first; on failure fall back to local proxies (HELPERNEXT_PROXY
# overrides the defaults). Completeness is decided solely by the sha256 check
# below, never by which path succeeded.
fetch() {
    curl -fSL --retry 2 --connect-timeout 10 --speed-time 30 --speed-limit 10240 -m 600 \
        ${1:+-x "$1"} -o "$zip_path.part" "$url" && mv "$zip_path.part" "$zip_path"
}
downloaded=0
for candidate in "" ${HELPERNEXT_PROXY:-} http://127.0.0.1:12450 http://127.0.0.1:17890; do
    if [ -n "$candidate" ]; then
        echo "Downloading $asset via proxy $candidate ..."
    else
        echo "Downloading $asset from GitHub directly ..."
    fi
    if fetch "$candidate"; then
        downloaded=1
        break
    fi
    rm -f "$zip_path" "$zip_path.part"
done
if [ "$downloaded" -ne 1 ]; then
    echo "Failed to download $url (tried direct and proxy fallbacks)" >&2
    exit 1
fi

actual_sha=$(shasum -a 256 "$zip_path" | awk '{print $1}')
if [ "$actual_sha" != "$expected_sha" ]; then
    echo "Release asset checksum mismatch: expected $expected_sha, got $actual_sha" >&2
    exit 1
fi
echo "Checksum OK: $asset = $expected_sha"

python3 "$app_root/scripts/install-helpernext.py" "$zip_path" "$app_root/app/helpernext"
echo "HelperNext vendor is now $asset (component $version, tag $tag)."
