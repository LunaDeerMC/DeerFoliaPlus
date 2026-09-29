package org.leavesmc.leaves.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.bot.agent.BotAction;
import org.leavesmc.leaves.bot.agent.actions.CraftBotAction;
import org.leavesmc.leaves.entity.botaction.LeavesBotAction;
import org.leavesmc.leaves.event.bot.BotActionStopEvent;
import org.leavesmc.leaves.event.bot.BotRemoveEvent;

import java.util.UUID;

public class CraftBot extends CraftPlayer implements Bot {

    public CraftBot(CraftServer server, ServerBot entity) {
        super(server, entity);
    }

    @Override
    public String getSkinName() {
        return this.getHandle().createState.skinName();
    }

    @Override
    public @NotNull String getRealName() {
        return this.getHandle().createState.realName();
    }

    @Override
    public @Nullable UUID getCreatePlayerUUID() {
        return this.getHandle().createPlayer;
    }

    @Override
    public void addAction(@NotNull LeavesBotAction action) {
        BotAction<?> internalAction = CraftBotAction.asInternalCopy(action);
        this.getHandle().getBukkitEntity().taskScheduler.schedule(
                (entity) -> this.getHandle().addBotAction(internalAction, null),
                null,
                1L
        );
    }

    @Override
    public LeavesBotAction getAction(int index) {
        return CraftBotAction.asAPICopy(this.getHandle().getBotActions().get(index));
    }

    @Override
    public int getActionSize() {
        return this.getHandle().getBotActions().size();
    }

    @Override
    public void stopAction(int index) {
        this.getHandle().getBukkitEntity().taskScheduler.schedule((entity) -> {
            if (index >= 0 && index < this.getHandle().getBotActions().size()) {
                this.getHandle().getBotActions().get(index).stop(this.getHandle(), BotActionStopEvent.Reason.PLUGIN);
            }
        }, null, 1L);
    }

    @Override
    public void stopAllActions() {
        this.getHandle().getBukkitEntity().taskScheduler.schedule((entity) -> {
            for (BotAction<?> action : this.getHandle().getBotActions()) {
                action.stop(this.getHandle(), BotActionStopEvent.Reason.PLUGIN);
            }
        }, null, 1L);
    }

    @Override
    public boolean remove(boolean save) {
        BotList.INSTANCE.removeBot(this.getHandle(), BotRemoveEvent.RemoveReason.PLUGIN, null, save);
        return true;
    }

    @Override
    public ServerBot getHandle() {
        return (ServerBot) entity;
    }

    public void setHandle(final ServerBot entity) {
        super.setHandle(entity);
    }

    @Override
    public String toString() {
        return "CraftBot{" + "name=" + getName() + '}';
    }
}
