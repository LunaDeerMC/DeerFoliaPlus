package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            int duration = ServerLevel.RAIN_DURATION.sample(source.getLevel().getRandom());
            source.getLevel().setWeatherParameters(0, duration, true, false);
            source.sendSuccess(() -> Component.translatable("commands.weather.set.rain"), true);
        });
        return 1;
    }
}
