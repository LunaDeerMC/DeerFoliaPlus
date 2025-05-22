package org.leavesmc.leaves.bot.agent.actions;

import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.ServerBot;

public class UseItemAction extends AbstractTimerAction<UseItemAction> {

    public UseItemAction() {
        super("use", UseItemAction::new);
    }

    @Override
    public boolean doTick(@NotNull ServerBot bot) {
        if (bot.isUsingItem()) {
            return false;
        }
        bot.swing(InteractionHand.MAIN_HAND);
        bot.updateItemInHand(InteractionHand.MAIN_HAND);
        return bot.gameMode.useItem(bot, bot.level(), bot.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND).consumesAction();
    }
}
