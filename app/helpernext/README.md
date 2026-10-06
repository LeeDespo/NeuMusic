# Embedded HelperNext

Kotlin bindings and native libraries consumed from the official HelperNext Release
pinned in ../helpernext.lock.json; provenance and per-file SHA-256 checksums are in
manifest.json. These files contain no account data. Refreshed with
`scripts/update-helpernext.sh`, which downloads the pinned release asset, verifies its
sha256 against the lock, checks manifest componentVersion/gitCommit and file digests,
then swaps this directory atomically. Do not edit these files by hand and never mix
files from different HelperNext revisions: the Kotlin binding, JNI glue and all four
ABI libraries are one atomic set. Component license: GPL-3.0-or-later, see LICENSE.
