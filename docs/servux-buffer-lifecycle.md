# Servux buffer lifecycle fix

Servux structure synchronization previously allocated a new reference-counted buffer for each encoded fragment without releasing it. With Netty's Java 24 linker allocator this retained native memory after GC. Inbound fragment copies and unfinished upload sessions also lacked cleanup.

The fix uses owned byte arrays for structure and entity payload data. Codecs can safely encode the same payload repeatedly, after its source buffer is released, or after a failed send. Decoders do not retain the network buffer or allocate a separate reference-counted buffer. PacketSplitter owns its input and each outgoing fragment and releases them in `finally`; callbacks borrow the fragment only for the duration of the call. NBT serialization failures are also covered.

Client NBT uploads (C2S types 12/13) have never been implemented: the old code only accumulated them and logged `NOT Implemented` when complete. They are now consumed and ignored without allocating upload data or storing sessions. There are consequently no partial upload sessions to retain after malformed input, timeouts or disconnects. Metadata, structure overlays, entity/block queries, and server-to-client split payloads retain their existing wire formats.

Player-leave callbacks run even when their protocol is disabled, so switching off a protocol does not prevent cleanup of a departing player's subscriptions. Join and payload activation checks retain their existing behavior.

The affected release branches are `ver/1.21.11`, `ver/26.1.2`, `ver/26.2`, and `ver/26.3.x`. The common change is developed on 1.21.11 and cherry-picked to the three 26.x branches, preserving each branch's Minecraft API and unrelated changes. Branches 1.21.4, 1.21.6, 1.21.7 and 1.21.8 do not contain this Servux implementation.

The standalone regression harness in `tools/servux-regression` compiles these exact branch sources against the corresponding released runtime. It checks bounded repeated encoding/decoding, native and heap allocator deltas, wire contents, reusable payloads, source lifetime, fragmentation, exceptions, ignored uploads, concurrent encoding, and disabled-protocol logout cleanup. See its README for execution and evidence metadata. This is component integration testing against real server classes and dependencies; it does not substitute for a live-client production soak test.

Deploy a built core for the matching branch with a normal saved shutdown and restart. The code change prevents new leaks; it cannot recover native memory already lost by a running old process. No existing worlds or player data are changed by this fix.
