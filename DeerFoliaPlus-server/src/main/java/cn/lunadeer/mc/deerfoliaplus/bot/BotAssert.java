package cn.lunadeer.mc.deerfoliaplus.bot;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.ServerBot;

import java.util.UUID;

public class BotAssert {

    public static boolean assertAmount(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        if (player.isOp()) return true;
        BotList botList = BotList.INSTANCE;
        int count = 0;
        for (var bot : botList.bots) {
            if (bot.createPlayer != null && bot.createPlayer.equals(player.getUniqueId())) {
                count++;
            }
        }
        if (count >= DeerFoliaPlusConfiguration.fakePlayer.amountPerPlayer) {
            sender.sendMessage("The amount of bots you spawned has reached the limit: " + DeerFoliaPlusConfiguration.fakePlayer.amountPerPlayer);
            return false;
        }
        return true;
    }

    public static boolean assertControl(CommandSender sender, String botName) {
        if (!(sender instanceof Player player)) return true;
        if (player.isOp()) return true;
        ServerBot bot = BotList.INSTANCE.getBotByName(botName);
        if (bot == null) {
            UUID creator = UUID.fromString(BotList.INSTANCE.getSavedBotList().getCompound(botName).get().getStringOr("creator", ""));
            if (!creator.equals(player.getUniqueId())) {
                sender.sendMessage("You do not have permission to control this bot: " + botName);
                return false;
            }
            sender.sendMessage("Bot not found: " + botName);
            return false;
        }
        if (bot.createPlayer != null && !bot.createPlayer.equals(player.getUniqueId())) {
            sender.sendMessage("You do not have permission to control this bot: " + botName);
            return false;
        }
        return true;
    }

}
