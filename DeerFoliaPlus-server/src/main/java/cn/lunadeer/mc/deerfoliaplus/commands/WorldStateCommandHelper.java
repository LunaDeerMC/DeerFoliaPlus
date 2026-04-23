package cn.lunadeer.mc.deerfoliaplus.commands;

import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.threadedregions.RegionizedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.List;
import java.util.Optional;

final class WorldStateCommandHelper {

    private WorldStateCommandHelper() {
    }

    static int setTime(CommandSourceStack source, int totalTicks) {
        Optional<net.minecraft.core.Holder<WorldClock>> defaultClock = source.getLevel().dimensionTypeRegistration().value().defaultClock();
        if (defaultClock.isEmpty()) {
            source.sendFailure(Component.literal("This dimension does not expose a default world clock"));
            return 0;
        }

        net.minecraft.core.Holder<WorldClock> clock = defaultClock.get();
        RegionizedServer.getInstance().addTask(() -> {
            for (ServerLevel level : GlobalConfiguration.get().commands.timeCommandAffectsAllWorlds
                    ? source.getServer().getAllLevels()
                    : List.of(source.getLevel())) {
                long currentTime = level.clockManager().getTotalTicks(clock);
                TimeSkipEvent event = new TimeSkipEvent(level.getWorld(), TimeSkipEvent.SkipReason.COMMAND, totalTicks - currentTime);
                if (event.callEvent()) {
                    level.clockManager().setTotalTicks(clock, currentTime + event.getSkipAmount());
                }
            }

            source.sendSuccess(() -> Component.translatable("commands.time.set.absolute", clock.getRegisteredName(), totalTicks), true);
        });
        return totalTicks;
    }

    static int setWeather(CommandSourceStack source, int clearTime, int rainTime, boolean raining, boolean thundering, String messageKey) {
        RegionizedServer.getInstance().addTask(() -> {
            source.getServer().setWeatherParameters(source.getLevel(), clearTime, rainTime, raining, thundering);
            source.sendSuccess(() -> Component.translatable(messageKey), true);
        });
        return 1;
    }
}
