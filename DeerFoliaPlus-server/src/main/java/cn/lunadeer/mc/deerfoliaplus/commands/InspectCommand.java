package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class InspectCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("inspect")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.inspect"))
                        .then(
                                Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                        )
        );
    }

    private static int inspect(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer viewer = source.getPlayerOrException();
        InspectInventoryGUI.open(viewer.getBukkitEntity(), target);
        return 1;
    }
}
