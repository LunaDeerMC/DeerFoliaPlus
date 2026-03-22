package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GodCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("god")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.god"))
                        .executes(ctx -> toggleGod(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(
                                Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> toggleGod(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                        )
        );
    }

    private static int toggleGod(CommandSourceStack source, ServerPlayer target) {
        boolean newState = !target.isInvulnerable();
        String name = target.getScoreboardName();
        target.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
            ServerPlayer player = (ServerPlayer) nmsEntity;
            player.setInvulnerable(newState);
        }, null, 1L);
        source.sendSuccess(() -> Component.literal("God mode " + (newState ? "enabled" : "disabled") + " for " + name), true);
        return 1;
    }
}
