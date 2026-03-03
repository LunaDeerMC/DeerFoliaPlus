package org.leavesmc.leaves.bot.agent.actions;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

public class UseItemOnOffhandAction extends AbstractTimerAction<UseItemOnOffhandAction> {

    public UseItemOnOffhandAction() {
        super("use_on_offhand", UseItemOnOffhandAction::new);
    }

    @Override
    public boolean doTick(@NotNull ServerBot bot) {
        HitResult result = bot.getRayTrace(5, ClipContext.Fluid.NONE);
        if (result instanceof BlockHitResult blockHitResult) {
            BlockState state = bot.level().getBlockState(blockHitResult.getBlockPos());
            bot.swing(InteractionHand.OFF_HAND);
            if (state.getBlock() == Blocks.TRAPPED_CHEST) {
                BlockEntity entity = bot.level().getBlockEntity(blockHitResult.getBlockPos());
                if (entity instanceof TrappedChestBlockEntity chestBlockEntity) {
                    chestBlockEntity.startOpen(bot);
                    // DeerFoliaPlus start - use entity scheduler for Folia compatibility
                    bot.getBukkitEntity().getScheduler().runDelayed(MinecraftInternalPlugin.INSTANCE, (task) -> chestBlockEntity.stopOpen(bot), null, 1);
                    // DeerFoliaPlus end - use entity scheduler for Folia compatibility
                    return true;
                }
            } else {
                bot.updateItemInHand(InteractionHand.OFF_HAND);
                return bot.gameMode.useItemOn(bot, bot.level(), bot.getItemInHand(InteractionHand.OFF_HAND), InteractionHand.OFF_HAND, (BlockHitResult) result).consumesAction();
            }
        }
        return false;
    }
}
