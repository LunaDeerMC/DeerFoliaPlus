package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            int duration = ServerLevel.THUNDER_DURATION.sample(source.getLevel().getRandom());
            source.getLevel().setWeatherParameters(0, duration, true, true);
            source.sendSuccess(() -> Component.translatable("commands.weather.set.thunder"), true);
        });
        return 1;
    }
}
