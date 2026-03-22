package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SuicideCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("suicide")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.suicide"))
                        .executes(ctx -> suicide(ctx.getSource()))
        );
    }

    private static int suicide(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
            ServerPlayer p = (ServerPlayer) nmsEntity;
            p.kill((net.minecraft.server.level.ServerLevel) p.level());
        }, null, 1L);
        source.sendSuccess(() -> Component.literal("Goodbye..."), false);
        return 1;
    }
}
