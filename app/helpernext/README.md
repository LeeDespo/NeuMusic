# Embedded HelperNext

Generated Kotlin and native libraries from the same Rust checkout using BoltFFI 0.31.0.
Provenance and SHA-256 checksums are in manifest.json. These files contain no account data.
Rebuild together with `HELPERNEXT_SOURCE=/path/to/QQMusicApi_HelperNext scripts/update-helpernext.sh`.
Changing only Kotlin package names or replacing only a native library breaks the JNI contract.

The Rust source is available at https://github.com/LeeDespo/QQMusicApi_HelperNext; the manifest records
the base revision, any working-tree changes and an exact source digest. source.patch preserves
production source changes relative to that revision when building an uncommitted checkout. Include matching component
source when distributing a modified build. Component license: GPL-3.0-or-later, see LICENSE.
