package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;

public class RainCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("rain")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.rain"))
                        .executes(ctx -> setRain(ctx.getSource()))
        );
    }

    private static int setRain(CommandSourceStack source) {
        return WorldStateCommandHelper.setWeather(
                source,
                0,
                ServerLevel.RAIN_DURATION.sample(source.getLevel().getRandom()),
                true,
                false,
                "commands.weather.set.rain"
        );
    }
}
