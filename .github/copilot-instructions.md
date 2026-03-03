# Project Guidelines — DeerFoliaPlus

## Architecture

Fork chain: **Paper → Folia → DeerFolia → DeerFoliaPlus**. This project adds functionality features (not performance) on top of DeerFolia via a **patch-based** build system (`io.papermc.paperweight.patcher`).

| Module | Role |
|---|---|
| `DeerFoliaPlus-server/` | Server implementation — new files in `src/main/java/`, upstream modifications as patches |
| `DeerFoliaPlus-api/` | Public API additions (bot interfaces, events) |
| `DeerFolia-server/` | Upstream DeerFolia sources (config framework, async pathfinding) — **do not edit directly** |
| `paper-server/`, `paper-api/`, `folia-api/` | Upstream generated sources — **do not edit directly** |

### Patch Directories

- `DeerFoliaPlus-server/minecraft-patches/features/` — patches against vanilla Minecraft (NMS) sources
- `DeerFoliaPlus-server/paper-patches/features/` — patches against CraftBukkit / Paper server sources
- `DeerFoliaPlus-server/DeerFolia-patches/features/` — patches against DeerFolia server sources
- `DeerFoliaPlus-api/paper-patches/features/` — patches against Paper API (Bukkit/Server interfaces)

New standalone files go under `DeerFoliaPlus-server/src/main/java/` or `DeerFoliaPlus-api/src/main/java/` — **not** as patches.

## Code Style

- **Java 21** — toolchain and language level
- Package: `cn.lunadeer.mc.deerfoliaplus.*` for DeerFoliaPlus code; `org.leavesmc.leaves.*` for code ported from Leaves
- In patches, mark all modifications with comment markers:
  ```java
  // DeerFoliaPlus start - <description>
  <code>
  // DeerFoliaPlus end - <description>
  ```
  One-liners: `code; // DeerFoliaPlus - <description>`
- Use **fully qualified class names** in patch hunks instead of adding import statements — this reduces merge conflicts
- Visibility changes: `// DeerFoliaPlus - private -> public`
- Fake player detection: `player instanceof org.leavesmc.leaves.bot.ServerBot`

## Build and Test

```bash
# Apply all patches (required after clone or upstream update)
./gradlew applyAllPatches

# Build production server jar → DeerFoliaPlus-server/build/libs/
./gradlew createMojmapPaperclipJar

# Regenerate patch files after committing changes in DeerFoliaPlus-server or DeerFoliaPlus-api
./gradlew rebuildAllServerPatches

# Run a test server
./gradlew runServer

# Install to local Maven
./gradlew publishToMavenLocal
```

Upstream DeerFolia version is pinned in `gradle.properties` via `deerFoliaRef`. Update the hash and run `applyAllPatches` to pull upstream changes.

## Project Conventions

### Configuration System
Uses DeerFolia's reflection-based YAML config framework (`cn.lunadeer.mc.deerfolia.utils.configuration`). Define `ConfigurationPart` subclasses with annotated fields. Runtime config files:
- `config/deer-folia-plus.yml` — DeerFoliaPlus features (see `DeerFoliaPlusConfiguration.java`)
- `config/deer-folia.yml` — DeerFolia perf settings (inherited, don't modify)

Access settings via static fields: `DeerFoliaPlusConfiguration.fakePlayer.enable`, `DeerFoliaPlusConfiguration.bedrockStrongholdGeneration.enabled`.

### Adding Features
1. **New files only** → add under `src/main/java/`, commit directly
2. **Modifying upstream code** → edit in module dir, `git add . && git commit`, then `./gradlew rebuildAllServerPatches`
3. **Modifying existing patches** → use `git rebase -i base` workflow (see README.md)

### Key Source Locations
- Entry config: `DeerFoliaPlus-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/configurations/DeerFoliaPlusConfiguration.java`
- Bot system core: `DeerFoliaPlus-server/src/main/java/org/leavesmc/leaves/bot/`
- Bot API: `DeerFoliaPlus-api/src/main/java/org/leavesmc/leaves/entity/`
- Bot events: `DeerFoliaPlus-api/src/main/java/org/leavesmc/leaves/event/bot/`

## Integration Points

- **Brigadier** (`com.mojang:brigadier`) for `/bot` command registration
- **Adventure** (`net.kyori:adventure`) for text components and chat
- **Configurate** (`org.spongepowered:configurate-yaml`) for YAML handling
- **Paperweight** (`io.papermc.paperweight.patcher`) drives the entire patch/build lifecycle
- Brand identifier: `lunadeer:deerfoliaplus`
