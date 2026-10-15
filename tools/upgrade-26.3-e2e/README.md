# DeerFoliaPlus 26.3 upgrade integration harness

Run from the repository root with Java 25 selected. Build the server first, then:

```sh
./gradlew -I tools/upgrade-26.3-e2e/classpath.init.gradle :DeerFoliaPlus-server:e2eClasspath
python3 tools/upgrade-26.3-e2e/compile.py
python3 tools/upgrade-26.3-e2e/run.py
```

`--jar PATH` chooses an already built Paperclip jar; `--tag NAME` chooses a new isolated directory. All generated plugins, worlds, logs, and JSON results stay under `build/upgrade-26.3-e2e`. An existing run directory is never overwritten. The server binds loopback port 25579. An optional relay uses loopback port 25576 and fetches official Mojang HTTPS responses through `curl`; it does not fake keys or authentication. Set `JAVA_HOME` or put Java 25 on PATH. Optional JVM proxy/trust settings come from `GRADLE_USER_HOME/gradle.properties` or `PLUS_E2E_GRADLE_PROPERTIES`.

The TCP client and official-service relay are adapted from DeerFolia's `tools/lithium-phase1-e2e` harness. The client uses protocol 777 and derives packet IDs from this checkout's generated 26.3 protocol tables.

Coverage:

- Paperclip startup, DeerFoliaPlus branding, Minecraft version, generated/loaded Plus and inherited Lithium configuration.
- Actual TCP login and native command packets for fly/god/more/hat, four inventory menus, three posture modes and get-up, time and weather. God mode is toggled both ways while temporary invulnerability is active. Lying position locks are tested through actual movement packets and a displaced 26.3 teleport acknowledgement; look-only packets, jump/shift exit, and normal movement recovery are checked separately.
- Fake-player create and sneak action; all eight attack/break/use action variants verify real watcher swing packets against distinct hand, attack/interact animation components and durations; knockback marks velocity synchronization. Explicit save/load, creator UUID and inventory persistence, resident save on shutdown and restoration after restart, remove.
- Seven custom recipe types registered; shaped and all five single-input matches/outputs, plus custom-ID chain acceptance/rejection checked against the live NMS recipe manager. All seven RecipeHolder and per-serializer stream codecs are encoded/decoded with the live registry access.
- NeoForge recipe payload, Fabric supported-serializer request and response, Syncmatica version/feature handshake, Servux metadata request/response over the real TCP connection. Recipe payloads must include the custom recipe ID.
- Inherited Lithium lazy collector, crafter sleep/API wake/countdown/resleep, safe asynchronous path creation, owner-region completion and actual navigation movement.

World/entity assertions run on the owning Folia region. The external plugin logs explicit `PLUS_PASS`/`PLUS_FAIL` markers. Nonzero server exit and error/exception log entries fail the run; `summary.json` includes both startup and restart results, artifact SHA-256, assertions, packet and custom-channel counts.

These checks exercise server protocol handling and payload codecs. They do not establish compatibility with every third-party 26.3 client mod, full schematic transfer workflows, or whole-server performance. No performance benchmark is run.

For Gradle unit tests, ordinary network environments can run `./gradlew test` directly. In an environment requiring the discovery relay, start `python3 tools/upgrade-26.3-e2e/services_proxy.py` in a separate terminal first, then run `./gradlew -I tools/upgrade-26.3-e2e/proxy-tests.init.gradle test`. This optional init script changes only test-process discovery; it does not alter production configuration. Stop the manually started relay when testing finishes. The integration runner reuses an already listening relay and only terminates a relay that it started itself.
