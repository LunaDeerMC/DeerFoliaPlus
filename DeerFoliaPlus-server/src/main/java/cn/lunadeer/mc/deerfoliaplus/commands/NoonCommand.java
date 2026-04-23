package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class NoonCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("noon")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.noon"))
                        .executes(ctx -> WorldStateCommandHelper.setTime(ctx.getSource(), 6000))
        );
    }
}
