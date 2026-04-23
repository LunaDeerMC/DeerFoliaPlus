---
name: add-feature
description: Guide for adding new features to DeerFoliaPlus end-to-end, including configuration, source files, patches, and API. Use when implementing a new feature, adding configuration options, creating new bot actions, adding API interfaces, creating events, or porting features from other projects like Leaves. Triggers on new feature development, configuration addition, bot action creation, API design, event creation, or feature porting.
---

# Add Feature

End-to-end procedure for adding a new feature to DeerFoliaPlus. Choose the applicable sections based on what the feature requires.

## Decision: Standalone File vs Patch

- **New file** (no upstream modification): Add under `src/main/java/`, commit directly to main repo
- **Upstream modification**: Edit in module working dir, commit there, then `./gradlew rebuildAllServerPatches`
- **Both**: Add standalone files first, then create patches that reference them

Most features need both: standalone implementation files + patches to hook into upstream code.

## Step 1: Add Configuration

All features should be toggleable via config. Edit `DeerFoliaPlusConfiguration.java`:

**Location**: `DeerFoliaPlus-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/configurations/DeerFoliaPlusConfiguration.java`

### Option A: Simple config field

Add a field directly in `DeerFoliaPlusConfiguration`:

```java
@Comments("Description of the feature")
public static boolean myFeatureEnabled = false;
```

Access: `DeerFoliaPlusConfiguration.myFeatureEnabled`

### Option B: Config section (for features with multiple options)

Create a new configuration class:

```java
// DeerFoliaPlus-server/src/main/java/cn/lunadeer/mc/deerfoliaplus/configurations/MyFeatureConfiguration.java
package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.Comments;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;

public class MyFeatureConfiguration extends ConfigurationPart {
    @Comments("Enable my feature (default: false)")
    public boolean enabled = false;

    @Comments("Some numeric option (default: 10)")
    public int someOption = 10;
}
```

Register it in `DeerFoliaPlusConfiguration.java`:

```java
@Comments("My Feature - short description")
public static MyFeatureConfiguration myFeature = new MyFeatureConfiguration();
```

Access: `DeerFoliaPlusConfiguration.myFeature.enabled`

Config annotations:
- `@Comments("...")` — YAML comment above the field
- `@HandleManually` — skip automatic serialization (for Logger, etc.)
- `@PostProcess` — method runs after config loads (for registration side-effects)

YAML output (in `config/deer-folia-plus.yml`):
```yaml
# My Feature - short description
my-feature:
  # Enable my feature (default: false)
  enabled: false
  # Some numeric option (default: 10)
  some-option: 10
```

## Step 2: Implement the Feature

### Standalone source files

Place under `DeerFoliaPlus-server/src/main/java/`:

| Content | Package |
|---|---|
| DeerFoliaPlus features | `cn.lunadeer.mc.deerfoliaplus.*` |
| Code ported from Leaves | `org.leavesmc.leaves.*` |

Commit directly to the main repo (not as patches).

### Patching upstream code

When hooking the feature into Minecraft/Paper/DeerFolia code:

1. Ensure patches are applied: `./gradlew applyAllPatches`
2. Edit files in the module working directory (e.g., `DeerFoliaPlus-server/`)
3. **Always** wrap changes with markers:
   ```java
   // DeerFoliaPlus start - My feature hook
   if (cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration.myFeature.enabled) {
       cn.lunadeer.mc.deerfoliaplus.MyFeature.handle(this);
   }
   // DeerFoliaPlus end - My feature hook
   ```
4. Use FQN — do NOT add imports in patched files
5. Commit in module dir, rebuild patches (see `patch-workflow` skill)

## Step 3: Add API (if feature needs plugin access)

### API interfaces

Place in `DeerFoliaPlus-api/src/main/java/`:

```java
// org/leavesmc/leaves/entity/MyInterface.java  (for Leaves-ported features)
// cn/lunadeer/mc/deerfoliaplus/api/MyInterface.java  (for DeerFoliaPlus-original features)
```

### API events

Create event classes in `DeerFoliaPlus-api/src/main/java/org/leavesmc/leaves/event/`:

```java
package org.leavesmc.leaves.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class MyFeatureEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
```

### Exposing API via Bukkit/Server

If the API needs to be accessible via `Bukkit.getXxx()` or `Server.getXxx()`, this requires patching `paper-api` code:
1. Edit `Bukkit.java` and `Server.java` in the `paper-api/` working directory
2. Commit in `DeerFoliaPlus-api/`, rebuild patches

## Step 4: Build and Verify

```bash
# Full build
./gradlew createPaperclipJar

# Quick test server
./gradlew runServer
```

## Step 5: Commit Everything

1. Standalone source files → `git add` and `git commit` in main repo
2. Patch files (auto-generated) → `git add` and `git commit` in main repo
3. Use a descriptive commit message: `Add <feature-name>: <brief description>`

## Example: Adding a Bot Action

Bot actions follow a specific pattern. Create the action class:

```java
// DeerFoliaPlus-server/src/main/java/org/leavesmc/leaves/bot/agent/actions/MyAction.java
package org.leavesmc.leaves.bot.agent.actions;

import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.bot.agent.BotAction;

public class MyAction extends AbstractTimerAction {

    public MyAction() {
        super("my_action", 1);  // name, default tick interval
    }

    @Override
    public boolean doTick(ServerBot bot) {
        // Action logic — return true if action should continue
        return true;
    }
}
```

Register it in `Actions.java`:

```java
// In org.leavesmc.leaves.bot.agent.Actions
public static void registerAll() {
    // ... existing registrations
    register(new MyAction());
}
```

No patch needed — these are standalone source files.
