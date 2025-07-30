package org.leavesmc.leaves.bot.agent.configs;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.agent.BotConfig;
import org.leavesmc.leaves.command.CommandArgument;
import org.leavesmc.leaves.command.CommandArgumentResult;
import org.leavesmc.leaves.command.CommandArgumentType;

import java.util.List;

public class AlwaysSendDataConfig extends BotConfig<Boolean> {

    private boolean value;

    public AlwaysSendDataConfig() {
        super("always_send_data", CommandArgument.of(CommandArgumentType.BOOLEAN).setTabComplete(0, List.of("ture", "false")), AlwaysSendDataConfig::new);
        this.value = DeerFoliaPlusConfiguration.fakePlayer.alwaysSendData;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public void setValue(@NotNull CommandArgumentResult result) throws IllegalArgumentException {
        this.value = result.readBoolean(this.value);
    }

    @Override
    @NotNull
    public ValueOutput save(@NotNull ValueOutput nbt) {
        super.save(nbt);
        nbt.putBoolean("always_send_data", this.getValue());
        return nbt;
    }

    @Override
    public void load(@NotNull ValueInput nbt) {
        this.value = nbt.getBooleanOr("always_send_data", false);
    }
}
