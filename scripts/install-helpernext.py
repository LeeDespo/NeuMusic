#!/usr/bin/env python3
"""Install the pinned HelperNext release artifact into app/helpernext/ atomically.

Usage: install-helpernext.py <release.zip> <destination>

Verifies the archive's manifest.json (componentVersion/gitCommit against
app/helpernext.lock.json, ABI set, per-file SHA-256) and the required files
(Kotlin binding, all four ABI native libraries, LICENSE) before swapping the
destination directory in one rename. Never touches credentials.
"""
import hashlib, json, pathlib, shutil, sys, tempfile, zipfile

ABIS = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']
BINDING = 'kotlin/com/example/qqmusic_api_helper_next/QqmusicApiHelperNext.kt'
REQUIRED = [BINDING, 'LICENSE'] + [f'jniLibs/{abi}/libqqmusic_api_helper_next.so' for abi in ABIS]

zip_path, destination = map(pathlib.Path, sys.argv[1:3])
lock = json.loads((destination.parent / 'helpernext.lock.json').read_text())


def fail(message):
    raise SystemExit(f'install-helpernext: {message}')


def sha256(data):
    return hashlib.sha256(data).hexdigest()


with zipfile.ZipFile(zip_path) as archive:
    names = archive.namelist()
    roots = {name.split('/')[0] for name in names if name.strip('/')}
    if len(roots) != 1:
        fail(f'expected a single top-level directory in the archive, got {sorted(roots)}')
    root = roots.pop()

    manifest = json.loads(archive.read(f'{root}/manifest.json'))
    if manifest.get('componentVersion') != lock['version']:
        fail(f"manifest componentVersion {manifest.get('componentVersion')!r} != lock version {lock['version']!r}")
    # Release manifests carry no 'revision'; gitCommit is the lock's provenance key.
    if manifest.get('gitCommit') != lock['gitCommit']:
        fail(f"manifest gitCommit {manifest.get('gitCommit')!r} != lock gitCommit {lock['gitCommit']!r}")
    if manifest.get('abis') != ABIS:
        fail(f"manifest abis {manifest.get('abis')} != expected {ABIS}")

    files = manifest.get('files') or fail('manifest.json has no files map')
    for relative, digest in sorted(files.items()):
        member = f'{root}/{relative}'
        if member not in names:
            fail(f'manifest lists {relative} but it is absent from the archive')
        actual = sha256(archive.read(member))
        if actual != digest:
            fail(f'sha256 mismatch for {relative}: manifest {digest}, archive {actual}')

    for relative in REQUIRED:
        if relative not in files and f'{root}/{relative}' not in names:
            fail(f'required file {relative} is absent from the archive')

    stage = pathlib.Path(tempfile.mkdtemp(prefix='helpernext-stage-', dir=destination.parent))
    try:
        for name in names:
            if name.strip('/') == name and not name.endswith('/'):
                relative = name[len(root) + 1:]
                if not relative or relative in ('..',) or '/../' in f'/{relative}':
                    fail(f'unsafe archive member: {name}')
                target = stage / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
        (stage / 'README.md').write_text(
            '# Embedded HelperNext\n'
            '\n'
            'Kotlin bindings and native libraries consumed from the official HelperNext Release\n'
            'pinned in ../helpernext.lock.json; provenance and per-file SHA-256 checksums are in\n'
            'manifest.json. These files contain no account data. Refreshed with\n'
            '`scripts/update-helpernext.sh`, which downloads the pinned release asset, verifies its\n'
            'sha256 against the lock, checks manifest componentVersion/gitCommit and file digests,\n'
            'then swaps this directory atomically. Do not edit these files by hand and never mix\n'
            'files from different HelperNext revisions: the Kotlin binding, JNI glue and all four\n'
            'ABI libraries are one atomic set. Component license: GPL-3.0-or-later, see LICENSE.\n')
        previous = None
        if destination.exists():
            previous = pathlib.Path(tempfile.mkdtemp(prefix='helpernext-old-', dir=destination.parent)) / 'vendor'
            destination.rename(previous)
        try:
            stage.rename(destination)
        except OSError:
            if previous is not None:
                previous.rename(destination)
                previous = None
            raise
        stage = None
        if previous is not None:
            shutil.rmtree(previous.parent)
        print(f"Installed {root} -> {destination} "
              f"(component {lock['version']}, gitCommit {lock['gitCommit'][:12]}...)")
    finally:
        if stage is not None and stage.exists():
            shutil.rmtree(stage)
