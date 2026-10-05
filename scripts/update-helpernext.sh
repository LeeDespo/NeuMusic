#!/bin/sh
# Regenerate the Kotlin/JNI pair together from one component checkout.
set -eu
# Use the rustup toolchain whose Android standard libraries were installed.
export PATH="$HOME/.cargo/bin:$PATH"
app_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
source_root=${HELPERNEXT_SOURCE:-"$app_root/../QQMusicApi_HelperNext"}
if [ ! -f "$source_root/Cargo.toml" ]; then
    revision=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["revision"])' "$app_root/app/helpernext/manifest.json")
    source_hash=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["sourceSha256"])' "$app_root/app/helpernext/manifest.json")
    source_root="$app_root/.helpernext-build/$source_hash"
    verify_source() {
        python3 - "$1" "$source_hash" <<'VERIFY'
import hashlib, pathlib, sys
root = pathlib.Path(sys.argv[1]); digest = hashlib.sha256()
try:
    for path in sorted((root / 'src').rglob('*.rs')) + [root / 'Cargo.toml', root / 'Cargo.lock', root / 'boltffi.toml']:
        digest.update(str(path.relative_to(root)).encode()); digest.update(b'\0'); digest.update(path.read_bytes())
except OSError:
    sys.exit(1)
sys.exit(0 if digest.hexdigest() == sys.argv[2] else 1)
VERIFY
    }
    if ! verify_source "$source_root"; then
        mkdir -p "$app_root/.helpernext-build"
        task_build=$(mktemp -d "$app_root/.helpernext-build/rebuild.XXXXXX")
        trap 'rm -rf "$task_build"' EXIT HUP INT TERM
        git -C "$task_build" init -q
        git -C "$task_build" fetch https://github.com/LeeDespo/QQMusicApi_HelperNext "$revision"
        git -C "$task_build" checkout --detach FETCH_HEAD
        if [ -f "$app_root/app/helpernext/source.patch" ]; then
            git -C "$task_build" apply "$app_root/app/helpernext/source.patch"
        fi
        verify_source "$task_build" || { echo "Reconstructed HelperNext source checksum mismatch" >&2; exit 1; }
        rm -rf "$source_root"
        mv "$task_build" "$source_root"
        trap - EXIT HUP INT TERM
    fi
    verify_source "$source_root" || exit 1
fi
source_root=$(CDPATH= cd -- "$source_root" && pwd)
export ANDROID_HOME=${ANDROID_HOME:-"$HOME/Library/Android/sdk"}
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-"$ANDROID_HOME/ndk/27.3.13750724"}
(cd "$source_root" && boltffi pack android --release --deny-skipped)
python3 "$app_root/scripts/install-helpernext.py" "$source_root" "$app_root/app/helpernext"
