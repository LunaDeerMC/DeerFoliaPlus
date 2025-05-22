package org.leavesmc.leaves.bot.agent;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.bot.agent.configs.AlwaysSendDataConfig;
import org.leavesmc.leaves.bot.agent.configs.SimulationDistanceConfig;
import org.leavesmc.leaves.bot.agent.configs.SkipSleepConfig;
import org.leavesmc.leaves.bot.agent.configs.SpawnPhantomConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Configs<E> {

    private static final Map<String, Configs<?>> configs = new HashMap<>();

    public static final Configs<Boolean> SKIP_SLEEP = register(new SkipSleepConfig());
    public static final Configs<Boolean> ALWAYS_SEND_DATA = register(new AlwaysSendDataConfig());
    public static final Configs<Boolean> SPAWN_PHANTOM = register(new SpawnPhantomConfig());
    public static final Configs<Integer> SIMULATION_DISTANCE = register(new SimulationDistanceConfig());

    public final BotConfig<E> config;

    private Configs(BotConfig<E> config) {
        this.config = config;
    }

    @NotNull
    @Contract(pure = true)
    public static Collection<Configs<?>> getConfigs() {
        return configs.values();
    }

    @Nullable
    public static Configs<?> getConfig(String name) {
        return configs.get(name);
    }

    @NotNull
    private static <T> Configs<T> register(BotConfig<T> botConfig) {
        Configs<T> config = new Configs<>(botConfig);
        configs.put(botConfig.getName(), config);
        return config;
    }
}