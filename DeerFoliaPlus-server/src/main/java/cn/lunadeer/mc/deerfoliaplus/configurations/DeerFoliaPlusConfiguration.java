package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationFile;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationManager;
import cn.lunadeer.mc.deerfolia.utils.configuration.HandleManually;
import cn.lunadeer.mc.deerfolia.utils.configuration.PostProcess;
import com.mojang.logging.LogUtils;
import io.papermc.paper.configuration.PaperConfigurations;
import net.minecraft.server.MinecraftServer;
import org.leavesmc.leaves.bot.agent.Actions;
import org.slf4j.Logger;

import java.io.File;

public class DeerFoliaPlusConfiguration extends ConfigurationFile {

    @HandleManually
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void load() {
        try {
            ConfigurationManager.load(DeerFoliaPlusConfiguration.class, new File(PaperConfigurations.CONFIG_DIR, "deer-folia-plus.yml"));
        } catch (Exception e) {
            LOGGER.error("Failed to load DeerFolia configuration: {}", e.getMessage());
        }
    }

    public static FakePlayerConfiguration fakePlayer = new FakePlayerConfiguration();

    @PostProcess
    public void registerFakePlayerCommand() {
        MinecraftServer.getServer().server.getCommandMap().register("bot", "deerfoliaplus", new org.leavesmc.leaves.bot.BotCommand("bot"));
        MinecraftServer.getServer().server.syncCommands();
        Actions.registerAll();
    }

}
