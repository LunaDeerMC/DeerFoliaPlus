package cn.lunadeer.mc.deerfoliaplus.bot;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.ServerBot;

import java.util.UUID;

public class BotAssert {

    private static final String AMOUNT_PERMISSION_PREFIX = "bot.amount.";

    public static boolean assertAmount(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        if (player.isOp()) return true;
        int limit = getAmountLimit(player);
        BotList botList = BotList.INSTANCE;
        int count = 0;
        for (var bot : botList.bots) {
            if (bot.createPlayer != null && bot.createPlayer.equals(player.getUniqueId())) {
                count++;
            }
        }
        if (count >= limit) {
            sender.sendMessage("The amount of bots you spawned has reached the limit: " + limit);
            return false;
        }
        return true;
    }

    private static int getAmountLimit(Player player) {
        int limit = -1;
        for (org.bukkit.permissions.PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission();
            if (!perm.startsWith(AMOUNT_PERMISSION_PREFIX)) continue;
            try {
                int value = Integer.parseInt(perm.substring(AMOUNT_PERMISSION_PREFIX.length()));
                if (value > limit) {
                    limit = value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return limit >= 0 ? limit : DeerFoliaPlusConfiguration.fakePlayer.amountPerPlayer;
    }

    public static boolean assertOp(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        if (player.isOp()) return true;
        sender.sendMessage("You do not have permission to use this command");
        return false;
    }

    public static boolean assertControl(CommandSender sender, String botName) {
        if (!(sender instanceof Player player)) return true;
        if (player.isOp()) return true;
        ServerBot bot = BotList.INSTANCE.getBotByName(botName);
        if (bot == null) {
            // Bot is not loaded, check saved bot data for creator
            var savedCompound = BotList.INSTANCE.getSavedBotList().getCompound(botName);
            if (savedCompound.isEmpty()) {
                sender.sendMessage("Bot not found: " + botName);
                return false;
            }
            String creatorStr = savedCompound.get().getStringOr("creator", "");
            if (creatorStr.isEmpty()) {
                return true; // No creator info, allow access
            }
            UUID creator = UUID.fromString(creatorStr);
            if (!creator.equals(player.getUniqueId())) {
                sender.sendMessage("You do not have permission to control this bot: " + botName);
                return false;
            }
            return true; // Player is the creator of this saved bot
        }
        if (bot.createPlayer != null && !bot.createPlayer.equals(player.getUniqueId())) {
            sender.sendMessage("You do not have permission to control this bot: " + botName);
            return false;
        }
        return true;
    }

}
