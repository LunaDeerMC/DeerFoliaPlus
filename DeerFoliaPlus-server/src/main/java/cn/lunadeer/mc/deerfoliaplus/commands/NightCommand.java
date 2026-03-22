package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.commands.TimeCommand;

public class NightCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("night")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.night"))
                        .executes(ctx -> TimeCommand.setTime(ctx.getSource(), 13000))
        );
    }
}
