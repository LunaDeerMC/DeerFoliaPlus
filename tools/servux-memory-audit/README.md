# Servux memory audit

This is a diagnostic harness for the existing vulnerable implementation. A successful run means the documented observations were reproduced, **not** that the server is free of leaks. It does not modify server sources, start a Minecraft world, connect to a server, or alter production configuration.

From the repository root, with the prepared workspace runtime:

```sh
python3 tools/servux-memory-audit/run.py
```

For another extracted Paperclip installation, supply `--runtime-dir DIR` (containing `libraries/`), `--jar SERVER_JAR`, `--java-home JDK25`, and `--gradle-home GRADLE_HOME`. Compilation uses cached JetBrains annotations and JSR-305 from the Gradle home. No dependencies are downloaded. The default JAR is the prepared **26.2-DEV-f24ea8b** artifact, not a production download or a newly built 26.3 server.

The runner first tests the actual server JAR, then compiles all four unmodified Servux source files from the current checkout against that runtime and tests them on a separate classpath. This second run checks current source behavior; it is not a complete 26.3 build. Metadata records the JAR, dependency and source SHA-256 hashes, checkout commit, source equality, and loaded codec location.

Each scenario group uses a fresh JVM with Java native access enabled, `-Xmx256m`, `-XX:MaxDirectMemorySize=128m`, and a 60-second timeout. `MemoryProbe` requires the actual `CleanerJava24Linker` path; it fails if the environment selects another cleaner. No OOM is deliberately induced. Output goes to a new directory under `build/servux-memory-audit/`, including `metadata.json`, `results.json`, and each process's `output.log`.

Coverage:

- Real `StructuresPayload.CODEC.encode`: 64, 128, and 256 repetitions of 256 KiB, allocator direct-memory deltas, ordinary DirectBufferPool deltas, and explicit GC with a collected witness object.
- A comparison encoder that only replaces the intermediate copy with `writeBytes(input, input.readableBytes())`.
- Type-2 structure payload byte equivalence and source reader-index equivalence at nine lengths, including zero and fragment boundaries.
- Real `PacketSplitter.send → StructuresPayload.CODEC.encode`, reconstructing three outgoing fragments and checking their payload bytes.
- Eight receive/send lifecycle probes, including a receive-copy memory delta, incomplete sessions, length validation and callback exceptions.

Inputs and outputs owned by the harness are explicitly released; inaccessible intermediate copies remain leaked. Session cleanup by reflection is **test-only**. The production implementation has no corresponding cancellation API. Persistent malloc leakage is established by the measurements together with the Netty cleaner implementation; allocator counters alone would not establish the same conclusion for every other cleaner or a pooled allocator.

No real-client login/movement, actual player disconnect, concurrent Folia region execution, live NMT sampling, or full-server OOM test is included. See [the investigation report](../../docs/servux-memory-audit-2026-10-28.md) for findings and limits.
