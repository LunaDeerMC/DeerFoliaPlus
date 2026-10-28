# Servux resource regression tests

These tests compile the branch's four Servux production sources, `PlayerInvokerHolder`, and `FabricRecipeSyncProtocol` against an extracted, version-matched server JAR and its real dependencies. They then run ten independent, bounded JVMs. They do not start a world, connect to a server, replace a production JAR, or modify the tested branch's sources.

For the prepared 1.21.11 runtime:

```sh
python3 tools/servux-regression/run.py \
  --source-root /workspace/DeerFoliaPlus-1.21.11 \
  --runtime-dir /workspace/servux-runtime/1.21.11 \
  --java-home /usr/lib/jvm/java-21-openjdk-amd64 \
  --release 21
```

For a 26.x branch, select that branch's worktree and extracted runtime and use Java 25:

```sh
python3 tools/servux-regression/run.py \
  --source-root /path/to/branch-worktree \
  --runtime-dir /path/to/extracted-runtime \
  --java-home /path/to/jdk-25 \
  --compiler-home /path/to/jdk-25 \
  --release 25
```

`--runtime-dir` must contain `libraries/` and exactly one server JAR under `versions/`; otherwise specify `--jar PATH`. `--compiler-home` can differ from `--java-home`, allowing compilation with JDK 25's `--release 21` and execution on a Java 21 JRE. Compilation also uses cached JetBrains annotations and JSR-305 under `--gradle-home` (default: the prepared workspace Gradle home). No network downloads occur in this runner.

Each JVM has `-Xmx256m`, `-XX:MaxDirectMemorySize=128m`, native access enabled, and a 60-second timeout. The tests do not require a particular Netty cleaner; version-specific Java/Netty choices are recorded. All modes must exit successfully and produce assertion-backed results:

- Structure data at nine lengths, from zero through multiple fragment boundaries: wire contents, both heap/direct borrowed sources, source reader-index preservation, independence from later source mutation/release, repeated encoding, and retry after an undersized-output error.
- 256 structure encodes of 256 KiB, with zero Netty direct and heap-buffer allocation deltas at 64/128/256 iterations and after GC.
- 256 structure/entity fragment decodes of 256 KiB, with source buffers released before re-encoding, repeatable entity encoding, and zero direct/heap-buffer allocation deltas.
- Metadata, block-entity and entity SIMPLE NBT response wire formats and round trips.
- Unsupported C2S upload types 12/13: empty, malformed and large contents are consumed and ignored without player/session lookup or retained Netty allocations (320 requests).
- Actual packet splitting at five lengths, including empty input and three fragments: bytes preserved, source and fragments released, and captured payloads safely encoded after the callback returns. A callback exception must release both current fragment and source.
- `sendNbt`: a normal NBT round trip and a real serialization exception caused by an oversized modified-UTF string; no callback or retained allocation on failure.
- A disabled protocol skips join behavior but still runs player-leave cleanup through the actual invoker.
- Four concurrent workers repeatedly encode one shared structure payload and decode entity fragments without retained Netty allocations.
- Complete protocol-manager initialization with Fabric recipe sync disabled still validates and registers its receiver, both Servux codecs, and the Syncmatica codec. This catches handler signature errors that abort startup before Servux is registered.

Fresh evidence goes to `build/servux-regression/<UTC timestamp>/` in the selected source worktree. It includes compiler logs, per-mode logs, `results.json`, and `metadata.json` with source/runtime hashes, class-file major versions, Java/Netty versions and actual loaded class locations. The compiled production classes take precedence over the release JAR on the test classpath.

These are component integration regressions, not a complete Gradle build or a live Minecraft client/world test. Netty allocator deltas check resource ownership; they are not a measurement of whole-process RSS, all Java heap allocations, or every possible server leak. The prior `servux-memory-audit` tools document the original defect and intentionally assert vulnerable behavior; use this harness to validate the fix.
