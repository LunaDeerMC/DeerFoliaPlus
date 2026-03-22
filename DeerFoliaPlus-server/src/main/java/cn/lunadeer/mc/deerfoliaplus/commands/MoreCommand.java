package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class MoreCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("more")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.more"))
                        .executes(ctx -> setMore(ctx.getSource(), -1))
                        .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setMore(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))
                        )
        );
    }

    private static int setMore(CommandSourceStack source, int amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack mainHandItem = player.getMainHandItem();

        if (mainHandItem.isEmpty()) {
            source.sendFailure(Component.literal("You must hold an item in your main hand"));
            return 0;
        }

        int targetAmount;
        if (amount < 0) {
            int maxStack = mainHandItem.getMaxStackSize();
            targetAmount = maxStack > 1 ? maxStack : mainHandItem.getCount() + 1;
        } else {
            targetAmount = amount;
        }

        mainHandItem.setCount(targetAmount);
        source.sendSuccess(() -> Component.literal("Item count set to " + targetAmount), true);
        return targetAmount;
    }
}
