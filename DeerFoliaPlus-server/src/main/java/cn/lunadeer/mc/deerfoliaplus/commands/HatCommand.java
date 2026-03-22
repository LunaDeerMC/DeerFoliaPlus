package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class HatCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("hat")
                        .requires(source -> source.getBukkitSender().hasPermission("deerfoliaplus.command.hat"))
                        .executes(ctx -> setHat(ctx.getSource()))
        );
    }

    private static int setHat(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack mainHandItem = player.getMainHandItem();

        if (mainHandItem.isEmpty()) {
            source.sendFailure(Component.literal("You must hold an item in your main hand"));
            return 0;
        }

        ItemStack currentHelmet = player.getItemBySlot(EquipmentSlot.HEAD);
        player.setItemSlot(EquipmentSlot.HEAD, mainHandItem.copy());
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), currentHelmet);
        source.sendSuccess(() -> Component.literal("Item placed on your head"), true);
        return 1;
    }
}
