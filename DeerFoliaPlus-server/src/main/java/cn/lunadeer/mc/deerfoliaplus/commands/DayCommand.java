package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class DayCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("day")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.day"))
                        .executes(ctx -> WorldStateCommandHelper.setTime(ctx.getSource(), 1000))
        );
    }
}
