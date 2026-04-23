package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;

public class StormCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("storm")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.storm"))
                        .executes(ctx -> setStorm(ctx.getSource()))
        );
    }

    private static int setStorm(CommandSourceStack source) {
        return WorldStateCommandHelper.setWeather(
                source,
                0,
                ServerLevel.THUNDER_DURATION.sample(source.getLevel().getRandom()),
                true,
                true,
                "commands.weather.set.thunder"
        );
    }
}
