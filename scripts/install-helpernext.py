#!/usr/bin/env python3
"""Copy a matching Kotlin/native artifact set and provenance, never credentials."""
import hashlib, json, pathlib, shutil, subprocess, sys, tempfile
source, destination = map(pathlib.Path, sys.argv[1:])
output = source / 'dist/android'
abis = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']
for abi in abis:
    if not (output / 'jniLibs' / abi / 'libqqmusic_api_helper_next.so').is_file():
        raise SystemExit(f'Missing native artifact: {abi}')
stage = pathlib.Path(tempfile.mkdtemp(prefix='helpernext-', dir=destination.parent))
try:
    shutil.copytree(output / 'kotlin', stage / 'kotlin')
    shutil.copytree(output / 'jniLibs', stage / 'jniLibs')
    shutil.copy2(source / 'LICENSE', stage / 'LICENSE')
    revision = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    dirty = bool(subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain'], text=True).strip())
    if dirty:
        patch = subprocess.check_output(['git', '-C', str(source), 'diff', '--binary', 'HEAD', '--',
                                        'src', 'Cargo.toml', 'Cargo.lock', 'boltffi.toml'])
        untracked = subprocess.check_output(['git', '-C', str(source), 'ls-files', '--others', '--exclude-standard', '--', 'src'], text=True).splitlines()
        for name in untracked:
            path = source / name
            if path.suffix != '.rs':
                raise SystemExit(f'Untracked source needs explicit packaging: {name}')
            lines = path.read_text().splitlines()
            patch += (f'diff --git a/{name} b/{name}\nnew file mode 100644\n--- /dev/null\n+++ b/{name}\n'
                      f'@@ -0,0 +1,{len(lines)} @@\n' + ''.join('+' + line + '\n' for line in lines)).encode()
        if patch:
            (stage / 'source.patch').write_bytes(patch)
    digest = hashlib.sha256()
    for path in sorted((source / 'src').rglob('*.rs')) + [source / 'Cargo.toml', source / 'Cargo.lock', source / 'boltffi.toml']:
        digest.update(str(path.relative_to(source)).encode()); digest.update(b'\0'); digest.update(path.read_bytes())
    manifest = {'repository': 'https://github.com/LeeDespo/QQMusicApi_HelperNext', 'revision': revision,
                'workingTreeChanges': dirty, 'sourceSha256': digest.hexdigest(), 'boltffi': '0.31.0', 'minSdk': 24,
                'rustc': subprocess.check_output(['rustc', '--version'], text=True).strip(),
                'abis': abis, 'files': {str(p.relative_to(stage)): hashlib.sha256(p.read_bytes()).hexdigest()
                                      for p in sorted(stage.rglob('*')) if p.is_file()}}
    (stage / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (stage / 'README.md').write_text('''# Embedded HelperNext\n\nGenerated Kotlin and native libraries from the same Rust checkout using BoltFFI 0.31.0.\nProvenance and SHA-256 checksums are in manifest.json. These files contain no account data.\nRebuild together with `HELPERNEXT_SOURCE=/path/to/QQMusicApi_HelperNext scripts/update-helpernext.sh`.\nChanging only Kotlin package names or replacing only a native library breaks the JNI contract.\n\nThe Rust source is available at https://github.com/LeeDespo/QQMusicApi_HelperNext; the manifest records\nthe base revision, any working-tree changes and an exact source digest. source.patch preserves\nproduction source changes relative to that revision when building an uncommitted checkout. Include matching component\nsource when distributing a modified build. Component license: GPL-3.0-or-later, see LICENSE.\n''')
    if destination.exists(): shutil.rmtree(destination)
    stage.rename(destination)
finally:
    if stage.exists(): shutil.rmtree(stage)
