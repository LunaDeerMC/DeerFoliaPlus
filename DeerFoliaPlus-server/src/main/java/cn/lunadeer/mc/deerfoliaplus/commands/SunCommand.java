package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            int duration = ServerLevel.RAIN_DELAY.sample(source.getLevel().getRandom());
            source.getLevel().setWeatherParameters(duration, 0, false, false);
            source.sendSuccess(() -> Component.translatable("commands.weather.set.clear"), true);
        });
        return 1;
    }
}
