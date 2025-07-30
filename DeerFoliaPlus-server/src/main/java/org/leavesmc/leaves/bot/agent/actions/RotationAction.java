package org.leavesmc.leaves.bot.agent.actions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.bot.agent.BotAction;
import org.leavesmc.leaves.command.CommandArgument;
import org.leavesmc.leaves.command.CommandArgumentResult;
import org.leavesmc.leaves.command.CommandArgumentType;

import java.util.List;

public class RotationAction extends BotAction<RotateAction> {

    public RotationAction() {
        super("rotation", CommandArgument.of(CommandArgumentType.FLOAT, CommandArgumentType.FLOAT), RotateAction::new);
        this.setTabComplete(0, List.of("<yaw>"));
        this.setTabComplete(1, List.of("<pitch>"));
    }

    private float yaw;
    private float pitch;

    @Override
    public void loadCommand(@Nullable ServerPlayer player, @NotNull CommandArgumentResult result) {
        if (player == null) {
            return;
        }

        this.setYaw(result.readFloat(player.getYRot())).setPitch(result.readFloat(player.getXRot())).setTickDelay(0).setNumber(1);
    }

    public RotationAction setYaw(float yaw) {
        this.yaw = yaw;
        return this;
    }

    public RotationAction setPitch(float pitch) {
        this.pitch = pitch;
        return this;
    }

    @Override
    @NotNull
    public ValueOutput save(@NotNull ValueOutput nbt) {
        super.save(nbt);
        nbt.putFloat("yaw", this.yaw);
        nbt.putFloat("pitch", this.pitch);
        return nbt;
    }

    @Override
    public void load(@NotNull ValueInput nbt) {
        super.load(nbt);
        this.setYaw(nbt.getFloatOr("yaw", 0.0f)).setPitch(nbt.getFloatOr("pitch", 0.0f));
    }

    @Override
    public boolean doTick(@NotNull ServerBot bot) {
        bot.setRot(yaw, pitch);
        return true;
    }
}
