package cn.lunadeer.mc.deerfoliaplus.commands;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.leavesmc.leaves.bot.BotCreateState;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.bot.agent.Actions;
import org.leavesmc.leaves.bot.agent.BotAction;
import org.leavesmc.leaves.bot.agent.BotConfig;
import org.leavesmc.leaves.bot.agent.Configs;
import org.leavesmc.leaves.bot.agent.actions.CraftCustomBotAction;
import org.leavesmc.leaves.entity.Bot;
import org.leavesmc.leaves.event.bot.BotActionStopEvent;
import org.leavesmc.leaves.event.bot.BotConfigModifyEvent;
import org.leavesmc.leaves.event.bot.BotCreateEvent;
import org.leavesmc.leaves.event.bot.BotRemoveEvent;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

import java.util.*;
import java.util.function.Consumer;

import static cn.lunadeer.mc.deerfoliaplus.bot.BotAssert.assertAmount;
import static cn.lunadeer.mc.deerfoliaplus.bot.BotAssert.assertControl;
import static cn.lunadeer.mc.deerfoliaplus.bot.BotAssert.assertOp;

public class BotCommand {

    private static final SimpleCommandExceptionType ERROR_BOT_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("This fakeplayer is not in server"));
    private static final SimpleCommandExceptionType ERROR_NAME_ILLEGAL =
            new SimpleCommandExceptionType(Component.literal("This name is illegal"));
    private static final SimpleCommandExceptionType ERROR_PLAYER_EXISTS =
            new SimpleCommandExceptionType(Component.literal("This player is already in server"));
    private static final SimpleCommandExceptionType ERROR_NOT_PLAYER =
            new SimpleCommandExceptionType(Component.literal("This command can only be used by a player"));
    private static final SimpleCommandExceptionType ERROR_AMOUNT_LIMIT =
            new SimpleCommandExceptionType(Component.literal("Bot amount limit reached"));
    private static final SimpleCommandExceptionType ERROR_NO_PERMISSION =
            new SimpleCommandExceptionType(Component.literal("You do not have permission to use this command"));
    private static final SimpleCommandExceptionType ERROR_NO_CONTROL =
            new SimpleCommandExceptionType(Component.literal("You do not have permission to control this bot"));
    private static final DynamicCommandExceptionType ERROR_BOT_NOT_SAVED =
            new DynamicCommandExceptionType(name -> Component.literal("Fake player '" + name + "' is not saved"));
    private static final SimpleCommandExceptionType ERROR_INVALID_ACTION =
            new SimpleCommandExceptionType(Component.literal("Invalid action"));
    private static final SimpleCommandExceptionType ERROR_INVALID_INDEX =
            new SimpleCommandExceptionType(Component.literal("Invalid index"));
    private static final SimpleCommandExceptionType ERROR_INVALID_CONFIG =
            new SimpleCommandExceptionType(Component.literal("Invalid config"));
    private static final SimpleCommandExceptionType ERROR_CREATE_FAILED =
            new SimpleCommandExceptionType(Component.literal("Create fake player failed"));
    private static final SimpleCommandExceptionType ERROR_TP_FAILED =
            new SimpleCommandExceptionType(Component.literal("Failed to teleport fakeplayer"));
    private static final SimpleCommandExceptionType ERROR_NOT_SCHEDULED =
            new SimpleCommandExceptionType(Component.literal("This fakeplayer is not scheduled to be removed"));

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BOTS = (context, builder) -> {
        BotList botList = BotList.INSTANCE;
        if (botList == null) return builder.buildFuture();
        return SharedSuggestionProvider.suggest(
                botList.bots.stream().map(e -> e.getName().getString()),
                builder
        );
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SAVED_BOTS = (context, builder) -> {
        BotList botList = BotList.INSTANCE;
        if (botList == null) return builder.buildFuture();
        return SharedSuggestionProvider.suggest(botList.getSavedBotList().keySet(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ACTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Actions.getNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CONFIGS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Configs.getConfigs().stream().map(c -> c.config.getName()),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bot")
                .requires(source -> source.getBukkitSender().hasPermission("bukkit.command.bot")
                        && DeerFoliaPlusConfiguration.fakePlayer.enable);

        // /bot create <name> [skin_name]
        root.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> executeCreate(ctx, StringArgumentType.getString(ctx, "name"), null))
                        .then(Commands.argument("skin_name", StringArgumentType.word())
                                .executes(ctx -> executeCreate(ctx,
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "skin_name")))
                        )
                )
        );

        // /bot remove <name> [cancel | <hours> [minutes] [seconds]]
        root.then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .executes(ctx -> executeRemove(ctx, StringArgumentType.getString(ctx, "name")))
                        .then(Commands.literal("cancel")
                                .executes(ctx -> executeRemoveCancel(ctx, StringArgumentType.getString(ctx, "name")))
                        )
                        .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeRemoveDelayed(ctx,
                                        StringArgumentType.getString(ctx, "name"),
                                        IntegerArgumentType.getInteger(ctx, "hours"), 0, 0))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                        .executes(ctx -> executeRemoveDelayed(ctx,
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "hours"),
                                                IntegerArgumentType.getInteger(ctx, "minutes"), 0))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                .executes(ctx -> executeRemoveDelayed(ctx,
                                                        StringArgumentType.getString(ctx, "name"),
                                                        IntegerArgumentType.getInteger(ctx, "hours"),
                                                        IntegerArgumentType.getInteger(ctx, "minutes"),
                                                        IntegerArgumentType.getInteger(ctx, "seconds")))
                                        )
                                )
                        )
                )
        );

        // /bot action <name> list
        // /bot action <name> stop <index|all>
        // /bot action <name> <action_name> [args...]
        root.then(Commands.literal("action")
                .requires(source -> source.getBukkitSender().hasPermission("bukkit.command.bot.action"))
                .then(Commands.argument("bot_name", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .then(Commands.literal("list")
                                .executes(ctx -> executeActionList(ctx, StringArgumentType.getString(ctx, "bot_name")))
                        )
                        .then(Commands.literal("stop")
                                .then(Commands.literal("all")
                                        .executes(ctx -> executeActionStopAll(ctx, StringArgumentType.getString(ctx, "bot_name")))
                                )
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(ctx -> executeActionStop(ctx,
                                                StringArgumentType.getString(ctx, "bot_name"),
                                                IntegerArgumentType.getInteger(ctx, "index")))
                                )
                        )
                        .then(Commands.argument("action_name", StringArgumentType.word()).suggests(SUGGEST_ACTIONS)
                                .executes(ctx -> executeAction(ctx,
                                        StringArgumentType.getString(ctx, "bot_name"),
                                        StringArgumentType.getString(ctx, "action_name"),
                                        new String[0]))
                                .then(Commands.argument("action_args", StringArgumentType.greedyString())
                                        .executes(ctx -> executeAction(ctx,
                                                StringArgumentType.getString(ctx, "bot_name"),
                                                StringArgumentType.getString(ctx, "action_name"),
                                                StringArgumentType.getString(ctx, "action_args").split(" ")))
                                )
                        )
                )
        );

        // /bot config <name> <config> [value...]
        root.then(Commands.literal("config")
                .requires(source -> source.getBukkitSender().hasPermission("bukkit.command.bot.config"))
                .then(Commands.argument("bot_name_cfg", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .then(Commands.argument("config_name", StringArgumentType.word()).suggests(SUGGEST_CONFIGS)
                                .executes(ctx -> executeConfigGet(ctx,
                                        StringArgumentType.getString(ctx, "bot_name_cfg"),
                                        StringArgumentType.getString(ctx, "config_name")))
                                .then(Commands.argument("config_value", StringArgumentType.greedyString())
                                        .executes(ctx -> executeConfigSet(ctx,
                                                StringArgumentType.getString(ctx, "bot_name_cfg"),
                                                StringArgumentType.getString(ctx, "config_name"),
                                                StringArgumentType.getString(ctx, "config_value").split(" ")))
                                )
                        )
                )
        );

        // /bot save <name>
        root.then(Commands.literal("save")
                .requires(source -> source.getBukkitSender().hasPermission("bukkit.command.bot.save"))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .executes(ctx -> executeSave(ctx, StringArgumentType.getString(ctx, "name")))
                )
        );

        // /bot load <name>
        root.then(Commands.literal("load")
                .requires(source -> source.getBukkitSender().hasPermission("bukkit.command.bot.load"))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_SAVED_BOTS)
                        .executes(ctx -> executeLoad(ctx, StringArgumentType.getString(ctx, "name")))
                )
        );

        // /bot list [world]
        root.then(Commands.literal("list")
                .executes(ctx -> executeList(ctx, null))
                .then(Commands.argument("world", DimensionArgument.dimension())
                        .executes(ctx -> executeList(ctx, DimensionArgument.getDimension(ctx, "world")))
                )
        );

        // /bot tp <name> <target_player>
        // /bot tp <name> <pos> [world]
        root.then(Commands.literal("tp")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> executeTpToPlayer(ctx,
                                        StringArgumentType.getString(ctx, "name"),
                                        EntityArgument.getPlayer(ctx, "target")))
                        )
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> executeTpToPos(ctx,
                                        StringArgumentType.getString(ctx, "name"),
                                        Vec3Argument.getVec3(ctx, "pos"),
                                        null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(ctx -> executeTpToPos(ctx,
                                                StringArgumentType.getString(ctx, "name"),
                                                Vec3Argument.getVec3(ctx, "pos"),
                                                DimensionArgument.getDimension(ctx, "dimension")))
                                )
                        )
                )
        );

        // /bot tphere <name>
        root.then(Commands.literal("tphere")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .executes(ctx -> executeTpHere(ctx, StringArgumentType.getString(ctx, "name")))
                )
        );

        // /bot inventory <name>
        root.then(Commands.literal("inventory")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .executes(ctx -> executeOpenInventory(ctx,
                                StringArgumentType.getString(ctx, "name"),
                                org.leavesmc.leaves.bot.gui.BotInventoryViewType.INVENTORY))
                )
        );

        // /bot equipment <name>
        root.then(Commands.literal("equipment")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .executes(ctx -> executeOpenInventory(ctx,
                                StringArgumentType.getString(ctx, "name"),
                                org.leavesmc.leaves.bot.gui.BotInventoryViewType.EQUIPMENT))
                )
        );

        // /bot backpack <name>
        root.then(Commands.literal("backpack")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_BOTS)
                        .executes(ctx -> executeOpenInventory(ctx,
                                StringArgumentType.getString(ctx, "name"),
                                org.leavesmc.leaves.bot.gui.BotInventoryViewType.BACKPACK))
                )
        );

        dispatcher.register(root);
    }

    // ---- Helpers ----

    private static ServerBot requireBot(String name) throws CommandSyntaxException {
        BotList botList = BotList.INSTANCE;
        ServerBot bot = botList.getBotByName(name);
        if (bot == null) {
            throw ERROR_BOT_NOT_FOUND.create();
        }
        return bot;
    }

    private static void requireControl(CommandSourceStack source, String name) throws CommandSyntaxException {
        if (!assertControl(source.getBukkitSender(), name)) {
            throw ERROR_NO_CONTROL.create();
        }
    }

    private static void requireOp(CommandSourceStack source) throws CommandSyntaxException {
        if (!assertOp(source.getBukkitSender())) {
            throw ERROR_NO_PERMISSION.create();
        }
    }

    private static void requireAmount(CommandSourceStack source) throws CommandSyntaxException {
        if (!assertAmount(source.getBukkitSender())) {
            throw ERROR_AMOUNT_LIMIT.create();
        }
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) throws CommandSyntaxException {
        if (!(source.getBukkitSender() instanceof Player)) {
            throw ERROR_NOT_PLAYER.create();
        }
        return source.getPlayerOrException();
    }

    // ---- /bot create ----

    private static int executeCreate(CommandContext<CommandSourceStack> ctx, String name, String skinName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        requireAmount(source);

        if (!name.matches("^[a-zA-Z0-9_]{4,16}$")) {
            throw ERROR_NAME_ILLEGAL.create();
        }
        if (Bukkit.getPlayerExact(name) != null || BotList.INSTANCE.getBotByName(name) != null) {
            throw ERROR_PLAYER_EXISTS.create();
        }

        Location location;
        CommandSender sender = source.getBukkitSender();
        if (sender instanceof Player player) {
            location = player.getLocation();
        } else {
            location = Bukkit.getWorlds().getFirst().getSpawnLocation();
        }

        BotCreateState.Builder builder = BotCreateState.builder(name, location)
                .createReason(BotCreateEvent.CreateReason.COMMAND)
                .creator(sender);

        if (skinName != null) {
            builder.skinName(skinName);
        }

        Consumer<Bot> consumer = bot -> {
            if (bot != null) {
                source.sendSuccess(() -> Component.literal("Create fake player " + bot.getName() + " successfully"), false);
            } else {
                source.sendFailure(Component.literal("Create fake player failed"));
            }
        };

        builder.spawnWithSkin(consumer);
        return 1;
    }

    // ---- /bot remove ----

    private static int executeRemove(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(name);
        requireControl(source, name);
        BotList.INSTANCE.removeBot(bot, BotRemoveEvent.RemoveReason.COMMAND, source.getBukkitSender(), false);
        return 1;
    }

    private static int executeRemoveCancel(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(name);
        requireControl(source, name);
        if (bot.removeTask == null) {
            throw ERROR_NOT_SCHEDULED.create();
        }
        bot.removeTask.cancel();
        bot.removeTask = null;
        source.sendSuccess(() -> Component.literal("Remove cancel"), false);
        return 1;
    }

    private static int executeRemoveDelayed(CommandContext<CommandSourceStack> ctx, String name,
                                            int hours, int minutes, int seconds) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(name);
        requireControl(source, name);

        long time = ((long) hours) * 3600 * 20 + ((long) minutes) * 60 * 20 + ((long) seconds) * 20;

        boolean isReschedule = bot.removeTask != null;
        if (isReschedule) {
            bot.removeTask.cancel();
        }

        BotList botList = BotList.INSTANCE;
        CommandSender sender = source.getBukkitSender();
        bot.removeTask = bot.getBukkitEntity().getScheduler().runDelayed(MinecraftInternalPlugin.INSTANCE, (task) -> {
            bot.removeTask = null;
            botList.removeBot(bot, BotRemoveEvent.RemoveReason.COMMAND, sender, false);
        }, null, time);

        String msg = "This fakeplayer will be removed in " + hours + "h " + minutes + "m " + seconds + "s"
                + (isReschedule ? " (rescheduled)" : "");
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ---- /bot action ----

    private static int executeActionList(CommandContext<CommandSourceStack> ctx, String botName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(botName);
        requireControl(source, botName);

        source.sendSuccess(() -> Component.literal(bot.getScoreboardName() + "'s action list:"), false);
        List<BotAction<?>> actions = bot.getBotActions();
        for (int i = 0; i < actions.size(); i++) {
            int idx = i;
            source.sendSuccess(() -> Component.literal(idx + " " + actions.get(idx).getName()), false);
        }
        return actions.size();
    }

    private static int executeActionStopAll(CommandContext<CommandSourceStack> ctx, String botName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(botName);
        requireControl(source, botName);

        CommandSender sender = source.getBukkitSender();
        Set<BotAction<?>> forRemoval = new HashSet<>();
        for (int i = 0; i < bot.getBotActions().size(); i++) {
            BotAction<?> action = bot.getBotActions().get(i);
            BotActionStopEvent event = new BotActionStopEvent(
                    bot.getBukkitEntity(), action.getName(), action.getUUID(), BotActionStopEvent.Reason.COMMAND, sender
            );
            event.callEvent();
            if (!event.isCancelled()) {
                forRemoval.add(action);
            }
        }
        bot.getBotActions().removeAll(forRemoval);
        source.sendSuccess(() -> Component.literal(bot.getScoreboardName() + "'s action list cleared."), false);
        return forRemoval.size();
    }

    private static int executeActionStop(CommandContext<CommandSourceStack> ctx, String botName, int index) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(botName);
        requireControl(source, botName);

        if (index < 0 || index >= bot.getBotActions().size()) {
            throw ERROR_INVALID_INDEX.create();
        }

        BotAction<?> action = bot.getBotActions().get(index);
        BotActionStopEvent event = new BotActionStopEvent(
                bot.getBukkitEntity(), action.getName(), action.getUUID(), BotActionStopEvent.Reason.COMMAND, source.getBukkitSender()
        );
        event.callEvent();
        if (!event.isCancelled()) {
            bot.getBotActions().remove(index);
            source.sendSuccess(() -> Component.literal(bot.getScoreboardName() + "'s " + action.getName() + " stopped."), false);
        }
        return 1;
    }

    private static int executeAction(CommandContext<CommandSourceStack> ctx, String botName,
                                     String actionName, String[] actionArgs) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(botName);
        requireControl(source, botName);

        BotAction<?> action = Actions.getForName(actionName);
        if (action == null) {
            throw ERROR_INVALID_ACTION.create();
        }

        CraftPlayer player;
        if (source.getBukkitSender() instanceof CraftPlayer craftPlayer) {
            player = craftPlayer;
        } else {
            player = bot.getBukkitEntity();
        }

        BotAction<?> newAction;
        try {
            if (action instanceof CraftCustomBotAction customBotAction) {
                newAction = customBotAction.createCraft(player, actionArgs);
            } else {
                newAction = action.create();
                newAction.loadCommand(player.getHandle(), action.getArgument().parse(0, actionArgs));
            }
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Action create error: " + e.getMessage() + ", please check your arguments"));
            return 0;
        }

        if (newAction == null) {
            source.sendFailure(Component.literal("Action create error, please check your arguments"));
            return 0;
        }

        if (bot.addBotAction(newAction, source.getBukkitSender())) {
            source.sendSuccess(() -> Component.literal("Action " + actionName + " has been issued to " + bot.getName().getString()), false);
        }
        return 1;
    }

    // ---- /bot config ----

    private static int executeConfigGet(CommandContext<CommandSourceStack> ctx, String botName, String configName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(botName);
        requireControl(source, botName);

        Configs<?> configKey = Configs.getConfig(configName);
        if (configKey == null) {
            throw ERROR_INVALID_CONFIG.create();
        }

        BotConfig<?> config = bot.getConfig(configKey);
        config.getMessage().forEach(msg -> source.sendSuccess(() -> Component.literal(msg), false));
        return 1;
    }

    private static int executeConfigSet(CommandContext<CommandSourceStack> ctx, String botName,
                                        String configName, String[] args) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(botName);
        requireControl(source, botName);

        Configs<?> configKey = Configs.getConfig(configName);
        if (configKey == null) {
            throw ERROR_INVALID_CONFIG.create();
        }

        BotConfig<?> config = bot.getConfig(configKey);
        CommandSender sender = source.getBukkitSender();

        BotConfigModifyEvent event = new BotConfigModifyEvent(bot.getBukkitEntity(), config.getName(), args, sender);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }

        org.leavesmc.leaves.command.CommandArgumentResult result = config.getArgument().parse(0, args);
        try {
            config.setValue(result);
            config.getChangeMessage().forEach(msg -> source.sendSuccess(() -> Component.literal(msg), false));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ---- /bot save ----

    private static int executeSave(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerBot bot = requireBot(name);
        requireControl(source, name);

        BotList botList = BotList.INSTANCE;
        if (botList.removeBot(bot, BotRemoveEvent.RemoveReason.COMMAND, source.getBukkitSender(), true)) {
            source.sendSuccess(() -> Component.literal(bot.getScoreboardName() + " saved to " + bot.createState.realName()), false);
        }
        return 1;
    }

    // ---- /bot load ----

    private static int executeLoad(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        requireAmount(source);
        requireControl(source, name);

        BotList botList = BotList.INSTANCE;
        if (!botList.getSavedBotList().contains(name)) {
            throw ERROR_BOT_NOT_SAVED.create(name);
        }

        Consumer<ServerBot> consumer = bot -> {
            if (bot != null) {
                source.sendSuccess(() -> Component.literal("Load fake player " + bot.getName() + " successfully"), false);
            } else {
                source.sendFailure(Component.literal("Load fake player failed"));
            }
        };

        Bukkit.getAsyncScheduler().runNow(MinecraftInternalPlugin.INSTANCE, (task) -> {
            botList.loadNewBot(consumer, name);
        });
        return 1;
    }

    // ---- /bot list ----

    private static int executeList(CommandContext<CommandSourceStack> ctx, ServerLevel filterWorld) {
        CommandSourceStack source = ctx.getSource();
        BotList botList = BotList.INSTANCE;

        if (filterWorld != null) {
            // Filtered by specific world — show bots in that world grouped by creator
            org.bukkit.World bukkitWorld = filterWorld.getWorld();
            Map<String, List<String>> creatorMap = new LinkedHashMap<>();
            int count = 0;
            for (ServerBot bot : botList.bots) {
                if (bot.getBukkitEntity().getWorld() == bukkitWorld) {
                    String creatorName = getCreatorName(bot);
                    creatorMap.computeIfAbsent(creatorName, k -> new ArrayList<>()).add(bot.getBukkitEntity().getName());
                    count++;
                }
            }
            int totalInWorld = count;

            source.sendSuccess(() -> Component.literal("========= " + bukkitWorld.getName() + " (" + totalInWorld + ") =========")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            sendCreatorGroup(source, creatorMap);
            return totalInWorld;
        }

        // Full list — group by world, then group by creator
        Map<org.bukkit.World, List<String>> worldMap = new LinkedHashMap<>();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            worldMap.put(world, new ArrayList<>());
        }
        Map<String, List<String>> creatorMap = new LinkedHashMap<>();

        for (ServerBot bot : botList.bots) {
            Bot bukkitBot = bot.getBukkitEntity();
            String botName = bukkitBot.getName();

            List<String> worldList = worldMap.get(bukkitBot.getWorld());
            if (worldList != null) {
                worldList.add(botName);
            }

            String creatorName = getCreatorName(bot);
            creatorMap.computeIfAbsent(creatorName, k -> new ArrayList<>()).add(botName);
        }

        // Header
        source.sendSuccess(() -> Component.literal("========= Bot List (" + botList.bots.size() + ") =========")
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);

        // By World
        source.sendSuccess(() -> Component.literal("[By World]").withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        for (var entry : worldMap.entrySet()) {
            org.bukkit.World w = entry.getKey();
            List<String> names = entry.getValue();
            if (names.isEmpty()) continue;
            net.minecraft.network.chat.MutableComponent line = Component.empty()
                    .append(Component.literal("  " + w.getName()).withStyle(net.minecraft.ChatFormatting.GREEN))
                    .append(Component.literal(" (" + names.size() + "): ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.literal(formatNameList(names)).withStyle(net.minecraft.ChatFormatting.WHITE));
            source.sendSuccess(() -> line, false);
        }

        // By Creator
        sendCreatorGroup(source, creatorMap);

        return botList.bots.size();
    }

    private static void sendCreatorGroup(CommandSourceStack source, Map<String, List<String>> creatorMap) {
        source.sendSuccess(() -> Component.literal("[By Creator]").withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        for (var entry : creatorMap.entrySet()) {
            String creator = entry.getKey();
            List<String> names = entry.getValue();
            net.minecraft.network.chat.MutableComponent line = Component.empty()
                    .append(Component.literal("  " + creator).withStyle(net.minecraft.ChatFormatting.AQUA))
                    .append(Component.literal(" (" + names.size() + "): ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(Component.literal(formatNameList(names)).withStyle(net.minecraft.ChatFormatting.WHITE));
            source.sendSuccess(() -> line, false);
        }
    }

    private static String getCreatorName(ServerBot bot) {
        if (bot.createPlayer != null) {
            org.bukkit.OfflinePlayer player = Bukkit.getOfflinePlayer(bot.createPlayer);
            String name = player.getName();
            if (name != null) return name;
        }
        return "Console";
    }

    // ---- /bot tp ----

    private static int executeTpToPlayer(CommandContext<CommandSourceStack> ctx, String name,
                                         ServerPlayer target) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        requireOp(source);
        ServerBot bot = requireBot(name);

        Location targetLoc = target.getBukkitEntity().getLocation();
        bot.getBukkitEntity().teleportAsync(targetLoc).thenAccept(success -> {
            if (success) {
                source.sendSuccess(() -> Component.literal("Teleported " + bot.getName().getString() + " to " + target.getName().getString()), true);
            } else {
                source.sendFailure(Component.literal("Failed to teleport " + bot.getName().getString()));
            }
        });
        return 1;
    }

    private static int executeTpToPos(CommandContext<CommandSourceStack> ctx, String name,
                                      Vec3 pos, ServerLevel dimension) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        requireOp(source);
        ServerBot bot = requireBot(name);

        ServerLevel world = dimension != null ? dimension : (ServerLevel) bot.level();
        Location targetLoc = new Location(world.getWorld(), pos.x, pos.y, pos.z,
                bot.getBukkitEntity().getLocation().getYaw(), bot.getBukkitEntity().getLocation().getPitch());

        bot.getBukkitEntity().teleportAsync(targetLoc).thenAccept(success -> {
            if (success) {
                source.sendSuccess(() -> Component.literal("Teleported " + bot.getName().getString()
                        + " to " + pos.x + ", " + pos.y + ", " + pos.z + " in " + world.getWorld().getName()), true);
            } else {
                source.sendFailure(Component.literal("Failed to teleport " + bot.getName().getString()));
            }
        });
        return 1;
    }

    // ---- /bot tphere ----

    private static int executeTpHere(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = requirePlayer(source);
        ServerBot bot = requireBot(name);
        requireControl(source, name);

        Location targetLoc = player.getBukkitEntity().getLocation();
        bot.getBukkitEntity().teleportAsync(targetLoc).thenAccept(success -> {
            if (success) {
                source.sendSuccess(() -> Component.literal("Teleported " + bot.getName().getString() + " to you"), true);
            } else {
                source.sendFailure(Component.literal("Failed to teleport " + bot.getName().getString()));
            }
        });
        return 1;
    }

    // ---- /bot inventory|equipment|backpack ----

    private static int executeOpenInventory(CommandContext<CommandSourceStack> ctx, String name,
                                            org.leavesmc.leaves.bot.gui.BotInventoryViewType viewType) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = requirePlayer(source);
        ServerBot bot = requireBot(name);
        requireControl(source, name);

        switch (viewType) {
            case INVENTORY -> org.leavesmc.leaves.bot.gui.BotInventoryGUI.openInventory(player.getBukkitEntity(), bot);
            case EQUIPMENT -> org.leavesmc.leaves.bot.gui.BotInventoryGUI.openEquipment(player.getBukkitEntity(), bot);
            case BACKPACK -> org.leavesmc.leaves.bot.gui.BotInventoryGUI.openBackpack(player.getBukkitEntity(), bot);
        }
        return 1;
    }

    // ---- Utilities ----

    private static String formatNameList(List<String> list) {
        if (list.isEmpty()) return "";
        String s = list.toString();
        return s.substring(1, s.length() - 1);
    }
}
