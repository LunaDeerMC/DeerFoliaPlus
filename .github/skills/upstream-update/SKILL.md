---
name: upstream-update
description: Guide for updating DeerFoliaPlus to a newer upstream DeerFolia version. Use when updating deerFoliaRef, pulling upstream changes, resolving patch conflicts after upstream update, or syncing with upstream DeerFolia/Folia/Paper. Triggers on upstream update, version bump, deerFoliaRef change, merge conflict resolution, or upstream sync.
---

# Upstream Update

Step-by-step procedure to update DeerFoliaPlus when the upstream DeerFolia project publishes new changes.

## Prerequisites

- Clean working tree (no uncommitted changes)
- All current patches applied successfully (`./gradlew applyAllPatches`)

## Update Procedure

### Step 1: Update the upstream reference

Edit `gradle.properties` in the project root. Change `deerFoliaRef` to the new upstream commit hash:

```properties
deerFoliaRef = <new-commit-hash>
```

Get the latest hash from the [DeerFolia repository](https://github.com/LunaDeerMC/DeerFolia) — use the full 40-char SHA of the target commit on the main branch.

### Step 2: Apply patches

```bash
./gradlew applyAllPatches
```

If this succeeds without errors, skip to Step 5.

### Step 3: Resolve conflicts

When patches fail to apply, Paperweight stops at the failing patch. For each conflict:

1. Check the terminal output for the failing patch file and module.
2. Manually apply the patch with reject tracking:
   ```bash
   git apply --reject <path/to/failing.patch>
   ```
3. Inspect generated `.rej` files — they contain the hunks that could not be applied.
4. Manually resolve each conflict in the source files by applying the intent of the `.rej` hunks.
5. Delete all `.rej` files after resolving.
6. Stage resolved files:
   ```bash
   git add .
   ```
7. Continue the patch series:
   ```bash
   git am --resolved
   ```

### Step 4: Repeat conflict resolution

If additional patches in the series also conflict, repeat Step 3 for each one until all patches are applied.

Common conflict causes after upstream update:
- **Context drift**: Upstream changed lines near the patch hunk — adjust context in resolved code
- **Moved code**: Upstream refactored or moved the target code — find the new location and apply changes there
- **Removed code**: Upstream deleted the code the patch modifies — evaluate if the patch is still needed
- **Import conflicts**: This is why we use FQN in patches — if imports conflict, convert to FQN

### Step 5: Rebuild patches

Once all patches apply cleanly:

```bash
./gradlew rebuildAllServerPatches
```

This regenerates all `.patch` files from the current module git history, incorporating any conflict resolutions.

### Step 6: Verify build

```bash
./gradlew createPaperclipJar
```

If the build fails, fix compilation errors in the module directories, amend the relevant commits, and rebuild patches again.

### Step 7: Commit

Commit all changes to the main repository:
- Updated `gradle.properties`
- Any regenerated `.patch` files
- Any new/modified source files

## Troubleshooting

### `invalid object` error during apply

This typically means the patch references a git object that doesn't exist in the new upstream. Use `git apply --reject` as described above.

### Build fails after successful patch apply

Upstream API changes may require updating DeerFoliaPlus source files:
1. Fix compilation errors in `DeerFoliaPlus-server/` or `DeerFoliaPlus-api/`
2. If fixes involve patched files, use the patch modification workflow (see `patch-workflow` skill)
3. If fixes involve standalone source files, commit directly

### Minecraft version bump

When upstream updates the Minecraft version (`mcVersion` in `gradle.properties`):
1. Also update `version` and `apiVersion` in `gradle.properties` to match
2. NMS patches are most likely to conflict due to Mojang code changes
3. Pay special attention to `minecraft-patches/features/` — these patch vanilla code directly
