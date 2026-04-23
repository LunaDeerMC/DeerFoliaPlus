package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;

public class SunCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sun")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.sun"))
                        .executes(ctx -> setSun(ctx.getSource()))
        );
    }

    private static int setSun(CommandSourceStack source) {
        return WorldStateCommandHelper.setWeather(
                source,
                ServerLevel.RAIN_DELAY.sample(source.getLevel().getRandom()),
                0,
                false,
                false,
                "commands.weather.set.clear"
        );
    }
}
