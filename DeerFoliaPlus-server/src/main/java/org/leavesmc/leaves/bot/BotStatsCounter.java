package org.leavesmc.leaves.bot;

import com.google.gson.JsonElement;
import com.mojang.datafixers.DataFixer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class BotStatsCounter extends ServerStatsCounter {

    private static final Path UNKOWN_PATH = Path.of("BOT_STATS_REMOVE_THIS");

    public BotStatsCounter(MinecraftServer server) {
        super(server, UNKOWN_PATH);
    }

    @Override
    public void save() {
    }

    @Override
    public void setValue(@NotNull Player player, @NotNull Stat<?> stat, int value) {
    }

    @Override
    public void parse(@NotNull DataFixer dataFixer, @NotNull JsonElement json) {
    }

    @Override
    public int getValue(@NotNull Stat<?> stat) {
        return 0;
    }
}
