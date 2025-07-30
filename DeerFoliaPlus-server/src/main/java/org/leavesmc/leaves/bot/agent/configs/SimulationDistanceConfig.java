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

public class SimulationDistanceConfig extends BotConfig<Integer> {

    public SimulationDistanceConfig() {
        super("simulation_distance", CommandArgument.of(CommandArgumentType.INTEGER).setTabComplete(0, List.of("2", "10", "<INT 2 - 32>")), SimulationDistanceConfig::new);
    }

    @Override
    public Integer getValue() {
        return this.bot.getBukkitEntity().getSimulationDistance();
    }

    @Override
    public void setValue(@NotNull CommandArgumentResult result) throws IllegalArgumentException {
        int realValue = result.readInt(this.bot.getBukkitEntity().getSimulationDistance());
        if (realValue < 2 || realValue > 32) {
            throw new IllegalArgumentException("simulation_distance must be a number between 2 and 32, got: " + result);
        }
        this.bot.getBukkitEntity().setSimulationDistance(realValue);
    }

    @Override
    @NotNull
    public ValueOutput save(@NotNull ValueOutput nbt) {
        super.save(nbt);
        nbt.putInt("simulation_distance", this.getValue());
        return nbt;
    }

    @Override
    public void load(@NotNull ValueInput nbt) {
        this.setValue(new CommandArgumentResult(new ArrayList<>() {{
            add(nbt.getIntOr("simulation_distance", 8));
        }}));
    }
}
