---
name: patch-workflow
description: Guide for creating, modifying, and rebuilding patches in DeerFoliaPlus. Use when working with patch files, editing upstream code, running git rebase workflows, rebuilding patches with Gradle, or troubleshooting patch conflicts. Triggers on patch creation, patch editing, rebuildAllServerPatches, applyAllPatches, git rebase, fixup, squash, or any patch-related git workflow.
---

# Patch Workflow

Procedural guide for all patch operations in DeerFoliaPlus. Patches are git-format-patch diffs maintained as `.patch` files, applied and rebuilt via Paperweight.

## Patch Directory Map

| Directory | Target codebase | When to use |
|---|---|---|
| `DeerFoliaPlus-server/minecraft-patches/features/` | Vanilla NMS (`net.minecraft.*`) | Modifying Minecraft server internals |
| `DeerFoliaPlus-server/paper-patches/features/` | CraftBukkit / Paper server | Modifying Paper/CraftBukkit server code |
| `DeerFoliaPlus-server/DeerFolia-patches/features/` | DeerFolia server | Modifying DeerFolia-specific server code |
| `DeerFoliaPlus-api/paper-patches/features/` | Paper API (Bukkit/Server) | Modifying Bukkit/Paper API interfaces |
| `DeerFoliaPlus-api/folia-patches/features/` | Folia API | Modifying Folia API interfaces |

Patch files are numbered sequentially: `0001-Title.patch`, `0002-Title.patch`, etc.

## Patch File Format

Standard git-format-patch with unified diff:

```
From <hash> Mon Sep 17 00:00:00 2001
From: Author <email>
Date: <date>
Subject: [PATCH] <Title>

diff --git a/path/to/File.java b/path/to/File.java
index abc1234..def5678 100644
--- a/path/to/File.java
+++ b/path/to/File.java
@@ -line,count +line,count @@ context
 unchanged line
-removed line
+added line
 unchanged line
```

## Modification Markers (CRITICAL)

All modifications to upstream code MUST use comment markers:

```java
// Multi-line:
// DeerFoliaPlus start - <description>
<modified code>
// DeerFoliaPlus end - <description>

// One-liner:
someCode(); // DeerFoliaPlus - <description>

// Visibility change:
public int field; // DeerFoliaPlus - private -> public
```

For code ported from Leaves, use `// Leaves start/end - <description>` markers.

## Import Rule

In patch hunks, use **fully qualified class names** instead of import statements to minimize merge conflicts:

```java
// WRONG: adding import in a patch
import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;

// CORRECT: use FQN inline
if (cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration.fakePlayer.enable) { ... }
```

## Creating a New Patch (Upstream Modification)

1. Ensure patches are applied: `./gradlew applyAllPatches`
2. Edit files in `DeerFoliaPlus-server/` or `DeerFoliaPlus-api/` (the module working directories — NOT patch files directly)
3. Stage and commit in the module directory:
   ```bash
   cd DeerFoliaPlus-server   # or DeerFoliaPlus-api
   git add .
   git commit -m "Descriptive patch title"
   ```
4. Return to project root and rebuild:
   ```bash
   cd ..
   ./gradlew rebuildAllServerPatches
   ```
5. New `.patch` file appears in the appropriate patches directory. Commit it to the main repo.

## Modifying an Existing Patch

### Method A: Rebase Edit (for any patch)

1. Enter the module directory:
   ```bash
   cd DeerFoliaPlus-server   # or DeerFoliaPlus-api
   ```
2. Start interactive rebase:
   ```bash
   git rebase -i base
   ```
3. Change `pick` to `edit` for the target patch commit. Save and exit.
4. Make changes to the source files.
5. Stage and amend (do NOT create a new commit):
   ```bash
   git add .
   git commit --amend
   ```
6. Continue rebase:
   ```bash
   git rebase --continue
   ```
7. Rebuild patches from project root:
   ```bash
   cd ..
   ./gradlew rebuildAllServerPatches
   ```

> Warning: During rebase, the project may not compile. This is expected.

### Method B: Fixup (for recent patches or small changes)

1. Make changes in the module directory and commit:
   ```bash
   cd DeerFoliaPlus-server
   git add .
   git commit --fixup <target-patch-hash>
   ```
   - Use `git log` to find the target patch hash
   - Use `git log --grep=<patch-name>` to search by title
   - Use `--squash` instead of `--fixup` to also update the commit message
2. Auto-squash rebase:
   ```bash
   git rebase -i --autosquash base
   ```
   Save and exit — the fixup commit is auto-positioned.
3. Rebuild from root:
   ```bash
   cd ..
   ./gradlew rebuildAllServerPatches
   ```

### Method C: Manual Reorder (when fixup positioning needs control)

1. Make changes, commit with any message.
2. Interactive rebase: `git rebase -i base`
3. Move the new commit below the target patch.
4. Change the new commit's action:
   - `f` / `fixup` — merge without changing message
   - `s` / `squash` — merge and update message
5. Save, exit, then rebuild patches.

## Rebuild Commands

```bash
# Rebuild all patch files from module git history
./gradlew rebuildAllServerPatches

# Apply all patches (after clone, upstream update, or reset)
./gradlew applyAllPatches
```

## Troubleshooting

### Patch fails to apply

If `applyAllPatches` fails with conflicts:

1. Check the error output for the failing patch file path.
2. Manually apply: `git apply --reject <patch-file>`
3. Inspect generated `.rej` files for conflict details.
4. Resolve conflicts in the source files.
5. Delete `.rej` files.
6. Stage resolved files: `git add .`
7. Continue: `git am --resolved`
8. Repeat if more patches conflict.

### Rebase conflicts

If `git rebase --continue` fails:
1. Resolve conflicts in the indicated files.
2. `git add .`
3. `git rebase --continue`
4. If unrecoverable: `git rebase --abort` starts over.
