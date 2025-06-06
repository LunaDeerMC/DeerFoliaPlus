package org.leavesmc.leaves.bot.agent.actions;

import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.ServerBot;

public class UseItemOffHandAction extends AbstractTimerAction<UseItemOffHandAction> {

    public UseItemOffHandAction() {
        super("use_offhand", UseItemOffHandAction::new);
    }

    @Override
    public boolean doTick(@NotNull ServerBot bot) {
        if (bot.isUsingItem()) {
            return false;
        }
        bot.swing(InteractionHand.OFF_HAND);
        bot.updateItemInHand(InteractionHand.OFF_HAND);
        return bot.gameMode.useItem(bot, bot.level(), bot.getItemInHand(InteractionHand.OFF_HAND), InteractionHand.OFF_HAND).consumesAction();
    }
}
