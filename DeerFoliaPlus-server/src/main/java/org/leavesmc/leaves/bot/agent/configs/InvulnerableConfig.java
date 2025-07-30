package org.leavesmc.leaves.bot.agent.configs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.agent.BotConfig;
import org.leavesmc.leaves.command.CommandArgument;
import org.leavesmc.leaves.command.CommandArgumentResult;
import org.leavesmc.leaves.command.CommandArgumentType;

import java.util.ArrayList;
import java.util.List;

public class InvulnerableConfig extends BotConfig<Boolean>  {
    public InvulnerableConfig() {
        super("invulnerable", CommandArgument.of(CommandArgumentType.BOOLEAN).setTabComplete(0, List.of("ture", "false")), InvulnerableConfig::new);

    }

    @Override
    public Boolean getValue() {
        return bot.isInvulnerable();
    }

    @Override
    public void setValue(@NotNull CommandArgumentResult result) throws IllegalArgumentException {
        bot.setInvulnerable(result.readBoolean(bot.isInvulnerable()));
    }

    @Override
    public @NotNull ValueOutput save(@NotNull ValueOutput nbt) {
        super.save(nbt);
        nbt.putBoolean("invulnerable", this.getValue());
        return nbt;
    }

    @Override
    public void load(@NotNull ValueInput nbt) {
        this.setValue(new CommandArgumentResult(new ArrayList<>() {{
            add(nbt.getBooleanOr("invulnerable", true));
        }}));
    }
}
