---
name: nms-command-dev
description: Guide for developing Minecraft NMS server commands using Brigadier in DeerFoliaPlus. Use when creating new commands, registering commands with the dispatcher, defining command arguments, handling permissions, or working with net.minecraft.server.commands package. Triggers on command registration, Brigadier usage, CommandSourceStack, CommandDispatcher, or NMS command development.
---

# NMS Command Development

Develop vanilla-style Minecraft commands using Mojang's Brigadier command framework within the NMS (net.minecraft.server) codebase.

## Core Architecture

### Key Classes

| Class | Package | Role |
|---|---|---|
| `Commands` | `net.minecraft.commands` | Static factories `literal()`, `argument()`, permission constants |
| `CommandDispatcher<CommandSourceStack>` | `com.mojang.brigadier` | Central registry, dispatches parsed input to handlers |
| `CommandSourceStack` | `net.minecraft.commands` | Execution context: who ran the command (player/console/entity), world, position |
| `CommandBuildContext` | `net.minecraft.commands` | Registry-aware context for argument types that need data packs (items, blocks, etc.) |

### Permission Levels

```java
Commands.LEVEL_ALL          // Everyone (e.g. /seed on integrated server)
Commands.LEVEL_MODERATORS   // Moderators
Commands.LEVEL_GAMEMASTERS  // Ops / game masters (most common)
Commands.LEVEL_ADMINS       // Admins
Commands.LEVEL_OWNERS       // Server owners
```

Custom permission check:

```java
public static final PermissionCheck PERMISSION_CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);
// Usage: .requires(Commands.hasPermission(PERMISSION_CHECK))
```

## Command File Structure

All command classes live under `net.minecraft.server.commands`. Each follows this pattern:

```java
package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class MyCommand {

    // register() signature variants:
    // 1. Simple — no registry context needed
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) { ... }

    // 2. With build context — needed for registry-backed arguments (items, blocks, effects, etc.)
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) { ... }

    // 3. With extra flag
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean isDedicatedServer) { ... }
}
```

## Registration Entry Point

Commands are registered in the `Commands` constructor (`net.minecraft.commands.Commands`). Add new command registration there:

```java
// In Commands constructor
MyCommand.register(this.dispatcher);
// or
MyCommand.register(this.dispatcher, context);
```

## Building Command Trees with Brigadier

### `Commands.literal(name)` — Subcommand / Keyword Node

Creates a fixed keyword node. Used for the root command name and subcommands.

### `Commands.argument(name, type)` — Argument Node

Creates a parsed-argument node. The `name` is used to retrieve the parsed value later.

### `.requires(predicate)` — Permission Gate

Attach to any node to restrict visibility and execution.

### `.executes(context -> ...)` — Execution Handler

The lambda receives `CommandContext<CommandSourceStack>`, must return `int` (success count, 0 = failure).

### `.then(child)` — Child Node

Chains sub-nodes to build a tree.

## Patterns by Complexity

### Minimal — No Arguments

```java
// /seed
public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("seed")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(ctx -> {
                long seed = ctx.getSource().getLevel().getSeed();
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("commands.seed.success", String.valueOf(seed)), false);
                return (int) seed;
            })
    );
}
```

### Single Argument

```java
// /kill [targets]
public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("kill")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(ctx -> kill(ctx.getSource(),
                ImmutableList.of(ctx.getSource().getEntityOrException())))  // default: self
            .then(
                Commands.argument("targets", EntityArgument.entities())
                    .executes(ctx -> kill(ctx.getSource(),
                        EntityArgument.getEntities(ctx, "targets")))
            )
    );
}
```

### Chained Arguments

```java
// /give <targets> <item> [count]
public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
    dispatcher.register(
        Commands.literal("give")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(
                Commands.argument("targets", EntityArgument.players())
                    .then(
                        Commands.argument("item", ItemArgument.item(context))
                            .executes(ctx -> giveItem(ctx.getSource(),
                                ItemArgument.getItem(ctx, "item"),
                                EntityArgument.getPlayers(ctx, "targets"), 1))
                            .then(
                                Commands.argument("count", IntegerArgumentType.integer(1))
                                    .executes(ctx -> giveItem(ctx.getSource(),
                                        ItemArgument.getItem(ctx, "item"),
                                        EntityArgument.getPlayers(ctx, "targets"),
                                        IntegerArgumentType.getInteger(ctx, "count")))
                            )
                    )
            )
    );
}
```

### Subcommand Branching

```java
// /time set|add|query ...
public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("time")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(
                Commands.literal("set")
                    .then(Commands.literal("day").executes(ctx -> setTime(ctx.getSource(), 1000)))
                    .then(Commands.literal("noon").executes(ctx -> setTime(ctx.getSource(), 6000)))
                    .then(Commands.argument("time", TimeArgument.time())
                        .executes(ctx -> setTime(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "time"))))
            )
            .then(
                Commands.literal("add")
                    .then(Commands.argument("time", TimeArgument.time())
                        .executes(ctx -> addTime(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "time"))))
            )
            .then(
                Commands.literal("query")
                    .then(Commands.literal("daytime").executes(ctx -> queryDaytime(ctx.getSource())))
                    .then(Commands.literal("gametime").executes(ctx -> queryGametime(ctx.getSource())))
            )
    );
}
```

### Literal Enum Modes (e.g. destroy/keep/replace)

```java
// /setblock <pos> <block> [destroy|keep|replace|strict]
Commands.argument("block", BlockStateArgument.block(buildContext))
    .executes(ctx -> setBlock(ctx.getSource(), ..., Mode.REPLACE, null))
    .then(Commands.literal("destroy")
        .executes(ctx -> setBlock(ctx.getSource(), ..., Mode.DESTROY, null)))
    .then(Commands.literal("keep")
        .executes(ctx -> setBlock(ctx.getSource(), ..., Mode.REPLACE, IS_EMPTY_PREDICATE)))
    .then(Commands.literal("replace")
        .executes(ctx -> setBlock(ctx.getSource(), ..., Mode.REPLACE, null)))
```

## Common Argument Types

### Brigadier Built-in (`com.mojang.brigadier.arguments`)

| Type | Factory | Getter |
|---|---|---|
| `int` | `IntegerArgumentType.integer()` / `.integer(min)` / `.integer(min, max)` | `IntegerArgumentType.getInteger(ctx, name)` |
| `long` | `LongArgumentType.longArg()` | `LongArgumentType.getLong(ctx, name)` |
| `float` | `FloatArgumentType.floatArg()` | `FloatArgumentType.getFloat(ctx, name)` |
| `double` | `DoubleArgumentType.doubleArg()` | `DoubleArgumentType.getDouble(ctx, name)` |
| `boolean` | `BoolArgumentType.bool()` | `BoolArgumentType.getBool(ctx, name)` |
| `String` (word) | `StringArgumentType.word()` | `StringArgumentType.getString(ctx, name)` |
| `String` (quoted) | `StringArgumentType.string()` | `StringArgumentType.getString(ctx, name)` |
| `String` (greedy) | `StringArgumentType.greedyString()` | `StringArgumentType.getString(ctx, name)` |

### Minecraft Arguments (`net.minecraft.commands.arguments`)

| Category | Type | Factory | Getter |
|---|---|---|---|
| **Entity** | `EntityArgument` | `.entity()` / `.entities()` / `.player()` / `.players()` | `getEntity()` / `getEntities()` / `getPlayer()` / `getPlayers()` |
| **Position** | `BlockPosArgument` | `.blockPos()` | `getBlockPos(ctx, name)` / `getLoadedBlockPos(ctx, name)` |
| **Position** | `Vec3Argument` | `.vec3()` | `getVec3(ctx, name)` / `getCoordinates(ctx, name)` |
| **Position** | `Vec2Argument` | `.vec2()` | `getVec2(ctx, name)` |
| **Position** | `ColumnPosArgument` | `.columnPos()` | `getColumnPos(ctx, name)` |
| **Rotation** | `RotationArgument` | `.rotation()` | `getRotation(ctx, name)` |
| **Block** | `BlockStateArgument` | `.block(buildCtx)` | `getBlock(ctx, name)` |
| **Block** | `BlockPredicateArgument` | `.blockPredicate(buildCtx)` | `getBlockPredicate(ctx, name)` |
| **Item** | `ItemArgument` | `.item(buildCtx)` | `getItem(ctx, name)` |
| **Effect** | `ResourceArgument` | `.resource(ctx, Registries.MOB_EFFECT)` | `getMobEffect(ctx, name)` |
| **Dimension** | `DimensionArgument` | `.dimension()` | `getDimension(ctx, name)` |
| **GameMode** | `GameModeArgument` | `.gameMode()` | `getGameMode(ctx, name)` |
| **Color** | `ColorArgument` | `.color()` | `getColor(ctx, name)` |
| **Component** | `ComponentArgument` | `.textComponent(buildCtx)` | `getResolvedComponent(ctx, name)` |
| **Message** | `MessageArgument` | `.message()` | `resolveChatMessage(ctx, name, callback)` |
| **NBT** | `CompoundTagArgument` | `.compoundTag()` | `getCompoundTag(ctx, name)` |
| **Time** | `TimeArgument` | `.time()` / `.time(min)` | via `IntegerArgumentType.getInteger()` |
| **UUID** | `UuidArgument` | `.uuid()` | `getUuid(ctx, name)` |
| **GameProfile** | `GameProfileArgument` | `.gameProfile()` | `getGameProfiles(ctx, name)` |
| **Score** | `ObjectiveArgument` | `.objective()` | `getObjective(ctx, name)` |
| **Team** | `TeamArgument` | `.team()` | `getTeam(ctx, name)` |
| **Anchor** | `EntityAnchorArgument` | `.anchor()` | `getAnchor(ctx, name)` |
| **Angle** | `AngleArgument` | `.angle()` | `getAngle(ctx, name)` |

## Sending Feedback

```java
// Success message (broadcast to ops if second arg = true)
source.sendSuccess(() -> Component.translatable("my.translation.key", args...), true);

// Failure message (never broadcasts)
source.sendFailure(Component.translatable("my.error.key"));
```

## Exception Handling

```java
// Define at class level
private static final SimpleCommandExceptionType ERROR_FAILED =
    new SimpleCommandExceptionType(Component.translatable("commands.mycommand.failed"));

private static final Dynamic2CommandExceptionType ERROR_TOO_LARGE =
    new Dynamic2CommandExceptionType(
        (max, actual) -> Component.translatableEscape("commands.mycommand.toobig", max, actual));

// Throw in handler
throw ERROR_FAILED.create();
throw ERROR_TOO_LARGE.create(maxValue, actualValue);
```

Exception types: `SimpleCommandExceptionType`, `DynamicCommandExceptionType`, `Dynamic2CommandExceptionType`, `Dynamic3CommandExceptionType`, `Dynamic4CommandExceptionType`.

## Command Aliases

Register the same tree under a different literal, or use `dispatcher.register(Commands.literal("alias").redirect(originalNode))`:

```java
// In TeleportCommand.register():
LiteralCommandNode<CommandSourceStack> tpNode = dispatcher.register(
    Commands.literal("teleport").requires(...).then(...)
);
dispatcher.register(Commands.literal("tp").redirect(tpNode));
```

## Folia Threading Considerations

In Folia/DeerFolia, entity operations must run on the entity's region thread. Use task schedulers:

```java
// Entity-scoped scheduling
entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
    // Safe to modify entity here
    nmsEntity.kill((ServerLevel) nmsEntity.level());
}, null, 1L);

// Global scheduling (e.g. time/weather)
io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
    // Runs on the global region thread
});
```

## DeerFoliaPlus Patch Conventions

When modifying existing command files via patches, follow the project markers:

```java
// DeerFoliaPlus start - <description>
<modified code>
// DeerFoliaPlus end - <description>
```

One-liner: `code; // DeerFoliaPlus - <description>`

Use fully qualified class names in patch hunks to avoid import conflicts.

New standalone command files go to `DeerFoliaPlus-server/src/main/java/` (not as patches).

## Complete Example — Custom Command

```java
package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class HealCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED =
        new SimpleCommandExceptionType(Component.translatable("commands.heal.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("heal")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> heal(
                            ctx.getSource(),
                            EntityArgument.getPlayers(ctx, "targets"),
                            -1))  // -1 = full heal
                        .then(
                            Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> heal(
                                    ctx.getSource(),
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    IntegerArgumentType.getInteger(ctx, "amount")))
                        )
                )
        );
    }

    private static int heal(CommandSourceStack source, Collection<ServerPlayer> targets, int amount)
            throws CommandSyntaxException {
        for (ServerPlayer player : targets) {
            player.getBukkitEntity().taskScheduler.schedule((ServerPlayer nmsEntity) -> {
                float healAmount = amount < 0
                    ? nmsEntity.getMaxHealth()
                    : (float) amount;
                nmsEntity.heal(healAmount);
            }, null, 1L);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable(
                "commands.heal.success.single",
                targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable(
                "commands.heal.success.multiple",
                targets.size()), true);
        }
        return targets.size();
    }
}
```

Register in `Commands` constructor:

```java
HealCommand.register(this.dispatcher);
```
