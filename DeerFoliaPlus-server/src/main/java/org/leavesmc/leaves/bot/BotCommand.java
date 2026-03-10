package org.leavesmc.leaves.bot;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import com.mojang.logging.LogUtils;
import io.papermc.paper.command.CommandUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.agent.Actions;
import org.leavesmc.leaves.bot.agent.BotAction;
import org.leavesmc.leaves.bot.agent.BotConfig;
import org.leavesmc.leaves.bot.agent.Configs;
import org.leavesmc.leaves.bot.agent.actions.CraftCustomBotAction;
import org.leavesmc.leaves.command.CommandArgumentResult;
import org.leavesmc.leaves.entity.Bot;
import org.leavesmc.leaves.event.bot.BotActionStopEvent;
import org.leavesmc.leaves.event.bot.BotConfigModifyEvent;
import org.leavesmc.leaves.event.bot.BotCreateEvent;
import org.leavesmc.leaves.event.bot.BotRemoveEvent;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;

import static cn.lunadeer.mc.deerfoliaplus.bot.BotAssert.assertAmount;
import static cn.lunadeer.mc.deerfoliaplus.bot.BotAssert.assertControl;
import static cn.lunadeer.mc.deerfoliaplus.bot.BotAssert.assertOp;
import static net.kyori.adventure.text.Component.text;

public class BotCommand extends Command {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private final Component unknownMessage;

    private static final String BOT_PERMISSION = "bukkit.command.bot";

    public BotCommand(String name) {
        super(name);
        this.description = "FakePlayer Command";
        this.usageMessage = "/bot [create | remove | action | list | config | tp | tphere | inventory | equipment | backpack]";
        this.unknownMessage = text("Usage: " + usageMessage, NamedTextColor.RED);
        // Don't use setPermission here - it controls Brigadier tree visibility
        // which causes tab completion to not work for non-OP players even if they
        // have the permission granted. Instead, check permissions in execute/tabComplete.
        final PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        if (pluginManager.getPermission(BOT_PERMISSION) == null) {
            pluginManager.addPermission(new Permission(BOT_PERMISSION, PermissionDefault.OP));
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args, Location location) throws IllegalArgumentException {
        if (!sender.hasPermission(BOT_PERMISSION) || !DeerFoliaPlusConfiguration.fakePlayer.enable) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        BotList botList = BotList.INSTANCE;

        if (args.length <= 1) {
            list.add("create");
            list.add("remove");
            list.add("action");
            list.add("config");
            list.add("save");
            list.add("load");
            list.add("list");
            list.add("tp");
            list.add("tphere");
            list.add("inventory");
            list.add("equipment");
            list.add("backpack");
        }

        if (args.length == 2) {
            switch (args[0]) {
                case "create" -> list.add("<BotName>");
                case "remove", "action", "config", "save", "tp", "tphere", "inventory", "equipment", "backpack" ->
                        list.addAll(botList.bots.stream().map(e -> e.getName().getString()).toList());
                case "list" -> list.addAll(Bukkit.getWorlds().stream().map(WorldInfo::getName).toList());
                case "load" -> list.addAll(botList.getSavedBotList().keySet());
            }
        }

        if (args.length == 3) {
            switch (args[0]) {
                case "action" -> {
                    list.add("list");
                    list.add("stop");
                    list.addAll(Actions.getNames());
                }
                case "create" -> list.add("<BotSkinName>");
                case "config" -> list.addAll(acceptConfig);
                case "remove" -> list.addAll(List.of("cancel", "[hour]"));
                case "tp" -> {
                    // suggest online player names and coordinates
                    list.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    if (sender instanceof Player player) {
                        list.add(String.valueOf(player.getLocation().getBlockX()));
                    } else {
                        list.add("<x>");
                    }
                }
            }
        }

        if (args.length == 4 && args[0].equals("tp")) {
            if (sender instanceof Player player) {
                list.add(String.valueOf(player.getLocation().getBlockY()));
            } else {
                list.add("<y>");
            }
        }

        if (args.length == 5 && args[0].equals("tp")) {
            if (sender instanceof Player player) {
                list.add(String.valueOf(player.getLocation().getBlockZ()));
            } else {
                list.add("<z>");
            }
        }

        if (args.length == 6 && args[0].equals("tp")) {
            list.addAll(Bukkit.getWorlds().stream().map(WorldInfo::getName).toList());
        }

        if (args[0].equals("remove") && args.length >= 3) {
            if (!Objects.equals(args[2], "cancel")) {
                switch (args.length) {
                    case 4 -> list.add("[minute]");
                    case 5 -> list.add("[second]");
                }
            }
        }

        if (args.length >= 4 && args[0].equals("action")) {
            ServerBot bot = botList.getBotByName(args[1]);

            if (bot == null) {
                return Collections.singletonList("<" + args[1] + " not found>");
            }

            if (args[2].equals("stop")) {
                list.add("all");
                for (int i = 0; i < bot.getBotActions().size(); i++) {
                    list.add(String.valueOf(i));
                }
            } else {
                BotAction<?> action = Actions.getForName(args[2]);
                if (action != null) {
                    list.addAll(action.getArgument().tabComplete(args.length - 4));
                }
            }
        }

        if (args.length >= 4 && args[0].equals("config")) {
            Configs<?> config = Configs.getConfig(args[2]);
            if (config != null) {
                list.addAll(config.config.getArgument().tabComplete(args.length - 4));
            }
        }

        return CommandUtil.getListMatchingLast(sender, args, list);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
        if (!sender.hasPermission(BOT_PERMISSION) || !DeerFoliaPlusConfiguration.fakePlayer.enable) return true;

        if (args.length == 0) {
            sender.sendMessage(unknownMessage);
            return false;
        }

        if (List.of("action", "config", "save", "load").contains(args[0]) && !sender.hasPermission(BOT_PERMISSION + "." + args[0])) {
            sender.sendMessage(unknownMessage);
            return false;
        }

        switch (args[0]) {
            case "create" -> this.onCreate(sender, args);
            case "remove" -> this.onRemove(sender, args);
            case "action" -> this.onAction(sender, args);
            case "config" -> this.onConfig(sender, args);
            case "list" -> this.onList(sender, args);
            case "save" -> this.onSave(sender, args);
            case "load" -> this.onLoad(sender, args);
            case "tp" -> this.onTp(sender, args);
            case "tphere" -> this.onTpHere(sender, args);
            case "inventory" -> this.onOpenInventory(sender, args, org.leavesmc.leaves.bot.gui.BotInventoryViewType.INVENTORY);
            case "equipment" -> this.onOpenInventory(sender, args, org.leavesmc.leaves.bot.gui.BotInventoryViewType.EQUIPMENT);
            case "backpack" -> this.onOpenInventory(sender, args, org.leavesmc.leaves.bot.gui.BotInventoryViewType.BACKPACK);
            default -> {
                sender.sendMessage(unknownMessage);
                return false;
            }
        }

        return true;
    }

    private void onCreate(CommandSender sender, String @NotNull [] args) {
        if (!assertAmount(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(text("Use /bot create <name> [skin_name] to create a fakeplayer", NamedTextColor.RED));
            return;
        }

        String botName = args[1];
        if (this.canCreate(sender, botName)) {
            BotCreateState.Builder builder = BotCreateState.builder(botName, Bukkit.getWorlds().getFirst().getSpawnLocation()).createReason(BotCreateEvent.CreateReason.COMMAND).creator(sender);

            if (args.length >= 3) {
                builder.skinName(args[2]);
            }

            if (sender instanceof Player player) {
                builder.location(player.getLocation());
            } else if (sender instanceof ConsoleCommandSender) {
                if (args.length >= 7) {
                    try {
                        World world = Bukkit.getWorld(args[3]);
                        double x = Double.parseDouble(args[4]);
                        double y = Double.parseDouble(args[5]);
                        double z = Double.parseDouble(args[6]);
                        if (world != null) {
                            builder.location(new Location(world, x, y, z));
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Can't build location", e);
                    }
                } else {
                    sender.sendMessage(text("Use /bot create <name> <skin_name> <world> <x> <y> <z> to create a fakeplayer in console", NamedTextColor.RED));
                    return;
                }
            }

            Consumer<Bot> consumer = bot -> {
                if (bot != null) {
                    sender.sendMessage(text("Create fake player " + bot.getName() + " successfully", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(text("Create fake player failed", NamedTextColor.RED));
                }
            };

            builder.spawnWithSkin(consumer);
        }
    }

    private boolean canCreate(CommandSender sender, @NotNull String name) {
        BotList botList = BotList.INSTANCE;
        if (!name.matches("^[a-zA-Z0-9_]{4,16}$")) {
            sender.sendMessage(text("This name is illegal", NamedTextColor.RED));
            return false;
        }

        if (Bukkit.getPlayerExact(name) != null || botList.getBotByName(name) != null) {
            sender.sendMessage(text("This player is in server", NamedTextColor.RED));
            return false;
        }

        return true;
    }

    private void onRemove(CommandSender sender, String @NotNull [] args) {
        if (args.length < 2 || args.length > 5) {
            sender.sendMessage(text("Use /bot remove <name> [hour] [minute] [second] to remove a fakeplayer", NamedTextColor.RED));
            return;
        }

        BotList botList = BotList.INSTANCE;
        ServerBot bot = botList.getBotByName(args[1]);

        if (bot == null) {
            sender.sendMessage(text("This fakeplayer is not in server", NamedTextColor.RED));
            return;
        }
        if (!assertControl(sender, args[1])) return;

        if (args.length > 2) {
            if (args[2].equals("cancel")) {
                // DeerFoliaPlus start - use Folia compatible scheduler
                if (bot.removeTask == null) {
                    sender.sendMessage(text("This fakeplayer is not scheduled to be removed", NamedTextColor.RED));
                    return;
                }
                bot.removeTask.cancel();
                bot.removeTask = null;
                // DeerFoliaPlus end - use Folia compatible scheduler
                sender.sendMessage(text("Remove cancel"));
                return;
            }

            long time = 0;
            int h; // Preventing out-of-range
            long s = 0;
            long m = 0;

            try {
                h = Integer.parseInt(args[2]);
                if (h < 0) {
                    throw new NumberFormatException();
                }
                time += ((long) h) * 3600 * 20;
                if (args.length > 3) {
                    m = Long.parseLong(args[3]);
                    if (m > 59 || m < 0) {
                        throw new NumberFormatException();
                    }
                    time += m * 60 * 20;
                }
                if (args.length > 4) {
                    s = Long.parseLong(args[4]);
                    if (s > 59 || s < 0) {
                        throw new NumberFormatException();
                    }
                    time += s * 20;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(text("This fakeplayer is not scheduled to be removed", NamedTextColor.RED));
                return;
            }

            // DeerFoliaPlus start - use Folia compatible entity scheduler
            boolean isReschedule = bot.removeTask != null;

            if (isReschedule) {
                bot.removeTask.cancel();
            }
            bot.removeTask = bot.getBukkitEntity().getScheduler().runDelayed(MinecraftInternalPlugin.INSTANCE, (task) -> {
                bot.removeTask = null;
                botList.removeBot(bot, BotRemoveEvent.RemoveReason.COMMAND, sender, false);
            }, null, time);
            // DeerFoliaPlus end - use Folia compatible entity scheduler

            sender.sendMessage("This fakeplayer will be removed in " + h + "h " + m + "m " + s + "s" + (isReschedule ? " (rescheduled)" : ""));

            return;
        }

        botList.removeBot(bot, BotRemoveEvent.RemoveReason.COMMAND, sender, false);
    }

    private void onAction(CommandSender sender, String @NotNull [] args) {
        if (args.length < 3) {
            sender.sendMessage(text("Use /bot action <name> <action> to make fakeplayer do action", NamedTextColor.RED));
            return;
        }

        ServerBot bot = BotList.INSTANCE.getBotByName(args[1]);
        if (bot == null) {
            sender.sendMessage(text("This fakeplayer is not in server", NamedTextColor.RED));
            return;
        }
        if (!assertControl(sender, args[1])) return;

        if (args[2].equals("list")) {
            sender.sendMessage(bot.getScoreboardName() + "'s action list:");
            for (int i = 0; i < bot.getBotActions().size(); i++) {
                sender.sendMessage(i + " " + bot.getBotActions().get(i).getName());
            }
            return;
        }

        if (args[2].equals("stop")) {
            if (args.length < 4) {
                sender.sendMessage(text("Invalid index", NamedTextColor.RED));
                return;
            }

            String index = args[3];
            if (index.equals("all")) {
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
                sender.sendMessage(bot.getScoreboardName() + "'s action list cleared.");
            } else {
                try {
                    int i = Integer.parseInt(index);
                    if (i < 0 || i >= bot.getBotActions().size()) {
                        sender.sendMessage(text("Invalid index", NamedTextColor.RED));
                        return;
                    }

                    BotAction<?> action = bot.getBotActions().get(i);
                    BotActionStopEvent event = new BotActionStopEvent(
                            bot.getBukkitEntity(), action.getName(), action.getUUID(), BotActionStopEvent.Reason.COMMAND, sender
                    );
                    event.callEvent();
                    if (!event.isCancelled()) {
                        bot.getBotActions().remove(i);
                        sender.sendMessage(bot.getScoreboardName() + "'s " + action.getName() + " stopped.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(text("Invalid index", NamedTextColor.RED));
                }
            }
            return;
        }

        BotAction<?> action = Actions.getForName(args[2]);
        if (action == null) {
            sender.sendMessage(text("Invalid action", NamedTextColor.RED));
            return;
        }

        CraftPlayer player;
        if (sender instanceof CraftPlayer) {
            player = (CraftPlayer) sender;
        } else {
            player = bot.getBukkitEntity();
        }

        String[] realArgs = new String[args.length - 3];
        if (realArgs.length != 0) {
            System.arraycopy(args, 3, realArgs, 0, realArgs.length);
        }

        BotAction<?> newAction = null;
        try {
            if (action instanceof CraftCustomBotAction customBotAction) {
                newAction = customBotAction.createCraft(player, realArgs);
            } else {
                newAction = action.create();
                newAction.loadCommand(player.getHandle(), action.getArgument().parse(0, realArgs));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(text("Action create error " + e.getMessage() + ", please check your arguments", NamedTextColor.RED));
            return;
        }

        if (newAction == null) {
            sender.sendMessage(text("Action create error, please check your arguments", NamedTextColor.RED));
            return;
        }

        if (bot.addBotAction(newAction, sender)) {
            sender.sendMessage("Action " + action.getName() + " has been issued to " + bot.getName().getString());
        }
    }

    private static final List<String> acceptConfig = Configs.getConfigs().stream().map(config -> config.config.getName()).toList();

    private void onConfig(CommandSender sender, String @NotNull [] args) {
        if (args.length < 3) {
            sender.sendMessage(text("Use /bot config <name> <config> to modify fakeplayer's config", NamedTextColor.RED));
            return;
        }

        ServerBot bot = BotList.INSTANCE.getBotByName(args[1]);
        if (bot == null) {
            sender.sendMessage(text("This fakeplayer is not in server", NamedTextColor.RED));
            return;
        }
        if (!assertControl(sender, args[1])) return;

        if (!acceptConfig.contains(args[2])) {
            sender.sendMessage(text("This config is not accept", NamedTextColor.RED));
            return;
        }

        BotConfig<?> config = bot.getConfig(Configs.getConfig(args[2]));
        if (args.length < 4) {
            config.getMessage().forEach(sender::sendMessage);
        } else {
            String[] realArgs = new String[args.length - 3];
            System.arraycopy(args, 3, realArgs, 0, realArgs.length);

            BotConfigModifyEvent event = new BotConfigModifyEvent(bot.getBukkitEntity(), config.getName(), realArgs, sender);
            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }
            CommandArgumentResult result = config.getArgument().parse(0, realArgs);

            try {
                config.setValue(result);
                config.getChangeMessage().forEach(sender::sendMessage);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(text(e.getMessage(), NamedTextColor.RED));
            }
        }
    }

    private void onSave(CommandSender sender, String @NotNull [] args) {
        if (args.length < 2) {
            sender.sendMessage(text("Use /bot save <name> to save a fakeplayer", NamedTextColor.RED));
            return;
        }

        BotList botList = BotList.INSTANCE;
        ServerBot bot = botList.getBotByName(args[1]);

        if (bot == null) {
            sender.sendMessage(text("This fakeplayer is not in server", NamedTextColor.RED));
            return;
        }
        if (!assertControl(sender, args[1])) return;

        if (botList.removeBot(bot, BotRemoveEvent.RemoveReason.COMMAND, sender, true)) {
            sender.sendMessage(bot.getScoreboardName() + " saved to " + bot.createState.realName());
        }
    }

    private void onLoad(CommandSender sender, String @NotNull [] args) {
        if (!assertAmount(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(text("Use /bot save <name> to save a fake player", NamedTextColor.RED));
            return;
        }

        if (!assertControl(sender, args[1])) return;
        String realName = args[1];
        BotList botList = BotList.INSTANCE;
        if (!botList.getSavedBotList().contains(realName)) {
            sender.sendMessage(text("This fake player is not saved", NamedTextColor.RED));
            return;
        }

        Consumer<ServerBot> consumer = bot -> {
            if (bot != null) {
                sender.sendMessage(text("Load fake player " + bot.getName() + " successfully", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(text("Load fake player failed", NamedTextColor.RED));
            }
        };
        Bukkit.getAsyncScheduler().runNow(MinecraftInternalPlugin.INSTANCE, (task) -> {
            botList.loadNewBot(consumer, realName);
        });
    }

    private void onList(CommandSender sender, String @NotNull [] args) {
        BotList botList = BotList.INSTANCE;
        if (args.length < 2) {
            Map<World, List<String>> botMap = new HashMap<>();
            for (World world : Bukkit.getWorlds()) {
                botMap.put(world, new ArrayList<>());
            }

            for (ServerBot bot : botList.bots) {
                Bot bukkitBot = bot.getBukkitEntity();
                botMap.get(bukkitBot.getWorld()).add(bukkitBot.getName());
            }

            sender.sendMessage("Total number: (" + botList.bots.size() + ")");
            for (World world : botMap.keySet()) {
                sender.sendMessage(world.getName() + "(" + botMap.get(world).size() + "): " + formatPlayerNameList(botMap.get(world)));
            }
        } else {
            World world = Bukkit.getWorld(args[1]);

            if (world == null) {
                sender.sendMessage(text("Unknown world", NamedTextColor.RED));
                return;
            }

            List<String> snowBotList = new ArrayList<>();
            for (ServerBot bot : botList.bots) {
                Bot bukkitBot = bot.getBukkitEntity();
                if (bukkitBot.getWorld() == world) {
                    snowBotList.add(bukkitBot.getName());
                }
            }

            sender.sendMessage(world.getName() + "(" + botList.bots.size() + "): " + formatPlayerNameList(snowBotList));
        }
    }

    private void onTp(CommandSender sender, String @NotNull [] args) {
        if (!assertOp(sender)) return;

        if (args.length < 3) {
            sender.sendMessage(text("Use /bot tp <name> <x> <y> <z> [world] or /bot tp <name> <player> to teleport a fakeplayer", NamedTextColor.RED));
            return;
        }

        BotList botList = BotList.INSTANCE;
        ServerBot bot = botList.getBotByName(args[1]);
        if (bot == null) {
            sender.sendMessage(text("This fakeplayer is not in server", NamedTextColor.RED));
            return;
        }

        if (args.length == 3) {
            // /bot tp <name> <player>
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(text("Player " + args[2] + " is not online", NamedTextColor.RED));
                return;
            }
            Location targetLoc = target.getLocation();
            bot.getBukkitEntity().teleportAsync(targetLoc).thenAccept(success -> {
                if (success) {
                    sender.sendMessage(text("Teleported " + bot.getName().getString() + " to " + target.getName(), NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(text("Failed to teleport " + bot.getName().getString(), NamedTextColor.RED));
                }
            });
            return;
        }

        if (args.length < 5) {
            sender.sendMessage(text("Use /bot tp <name> <x> <y> <z> [world] to teleport a fakeplayer to coordinates", NamedTextColor.RED));
            return;
        }

        // /bot tp <name> <x> <y> <z> [world]
        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);

            World world;
            if (args.length >= 6) {
                world = Bukkit.getWorld(args[5]);
                if (world == null) {
                    sender.sendMessage(text("Unknown world: " + args[5], NamedTextColor.RED));
                    return;
                }
            } else {
                world = bot.getBukkitEntity().getWorld();
            }

            Location targetLoc = new Location(world, x, y, z, bot.getBukkitEntity().getLocation().getYaw(), bot.getBukkitEntity().getLocation().getPitch());
            bot.getBukkitEntity().teleportAsync(targetLoc).thenAccept(success -> {
                if (success) {
                    sender.sendMessage(text("Teleported " + bot.getName().getString() + " to " + x + ", " + y + ", " + z + " in " + world.getName(), NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(text("Failed to teleport " + bot.getName().getString(), NamedTextColor.RED));
                }
            });
        } catch (NumberFormatException e) {
            sender.sendMessage(text("Invalid coordinates", NamedTextColor.RED));
        }
    }

    private void onTpHere(CommandSender sender, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("This command can only be used by a player", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(text("Use /bot tphere <name> to teleport a fakeplayer to you", NamedTextColor.RED));
            return;
        }

        BotList botList = BotList.INSTANCE;
        ServerBot bot = botList.getBotByName(args[1]);
        if (bot == null) {
            sender.sendMessage(text("This fakeplayer is not in server", NamedTextColor.RED));
            return;
        }
        if (!assertControl(sender, args[1])) return;

        Location targetLoc = player.getLocation();
        bot.getBukkitEntity().teleportAsync(targetLoc).thenAccept(success -> {
            if (success) {
                sender.sendMessage(text("Teleported " + bot.getName().getString() + " to you", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(text("Failed to teleport " + bot.getName().getString(), NamedTextColor.RED));
            }
        });
    }

    private void onOpenInventory(CommandSender sender, String @NotNull [] args, org.leavesmc.leaves.bot.gui.BotInventoryViewType viewType) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("This command can only be used by a player", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(text("Use /bot " + viewType.name().toLowerCase() + " <name> to open fakeplayer's " + viewType.getDisplayName().toLowerCase(), NamedTextColor.RED));
            return;
        }

        ServerBot bot = BotList.INSTANCE.getBotByName(args[1]);
        if (bot == null) {
            sender.sendMessage(text("This fakeplayer is not in server", NamedTextColor.RED));
            return;
        }
        if (!assertControl(sender, args[1])) return;

        switch (viewType) {
            case INVENTORY -> org.leavesmc.leaves.bot.gui.BotInventoryGUI.openInventory(player, bot);
            case EQUIPMENT -> org.leavesmc.leaves.bot.gui.BotInventoryGUI.openEquipment(player, bot);
            case BACKPACK -> org.leavesmc.leaves.bot.gui.BotInventoryGUI.openBackpack(player, bot);
        }
    }

    @NotNull
    private static String formatPlayerNameList(@NotNull List<String> list) {
        if (list.isEmpty()) {
            return "";
        }
        String string = list.toString();
        return string.substring(1, string.length() - 1);
    }
}

