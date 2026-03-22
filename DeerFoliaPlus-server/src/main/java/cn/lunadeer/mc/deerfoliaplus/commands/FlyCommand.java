package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class FlyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("fly")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.fly"))
                        .executes(ctx -> toggleFly(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(
                                Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> toggleFly(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                        )
        );
    }

    private static int toggleFly(CommandSourceStack source, ServerPlayer target) {
        boolean newState = !target.getAbilities().mayfly;
        String name = target.getScoreboardName();
        target.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
            ServerPlayer player = (ServerPlayer) nmsEntity;
            player.getAbilities().mayfly = newState;
            if (!newState) {
                player.getAbilities().flying = false;
            }
            player.onUpdateAbilities();
        }, null, 1L);
        source.sendSuccess(() -> Component.literal("Flight " + (newState ? "enabled" : "disabled") + " for " + name), true);
        return 1;
    }
}
