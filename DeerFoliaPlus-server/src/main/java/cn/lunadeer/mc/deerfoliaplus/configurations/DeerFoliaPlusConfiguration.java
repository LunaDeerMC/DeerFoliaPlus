package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.*;
import com.mojang.logging.LogUtils;
import io.papermc.paper.configuration.PaperConfigurations;
import org.leavesmc.leaves.bot.agent.Actions;
import org.slf4j.Logger;

import java.io.File;

public class DeerFoliaPlusConfiguration extends ConfigurationFile {

    @HandleManually
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void load() {
        try {
            File file = new File(PaperConfigurations.CONFIG_DIR, "deer-folia-plus.yml");
            StructureGenerationFixesConfiguration.validate(file);
            ConfigurationManager.load(DeerFoliaPlusConfiguration.class, file);
        } catch (Exception e) {
            structureGenerationFixes.enabled = false;
            LOGGER.error("Failed to load DeerFoliaPlus configuration; structure generation repairs have been disabled", e);
        }
        cn.lunadeer.mc.deerfoliaplus.worldgen.StructureGenerationFixes.initialize(structureGenerationFixes);
    }

    public static FakePlayerConfiguration fakePlayer = new FakePlayerConfiguration();

    @Comments("Vanilla structure generation repairs - master and individual switches; restart required")
    public static StructureGenerationFixesConfiguration structureGenerationFixes = new StructureGenerationFixesConfiguration();

    @PostProcess
    public void registerFakePlayer() {
        Actions.registerAll();
        // Register bot inventory GUI listener
        org.bukkit.Bukkit.getPluginManager().registerEvents(
                new org.leavesmc.leaves.bot.gui.BotInventoryListener(),
                org.leavesmc.leaves.plugin.MinecraftInternalPlugin.INSTANCE
        );
    }

    @PostProcess
    public void registerInspectInventoryListener() {
        // Register inspect inventory GUI listener
        org.bukkit.Bukkit.getPluginManager().registerEvents(
                new cn.lunadeer.mc.deerfoliaplus.commands.InspectInventoryListener(),
                org.leavesmc.leaves.plugin.MinecraftInternalPlugin.INSTANCE
        );
    }

    @PostProcess
    public void registerUtilityCommandPermissions() {
        cn.lunadeer.mc.deerfoliaplus.commands.MenuCommandPermissions.register();
    }

    @Comments("Bedrock-style Stronghold Generation - Random unlimited distribution instead of 128 in rings")
    public static BedrockStrongholdGeneration bedrockStrongholdGeneration = new BedrockStrongholdGeneration();

    public static class BedrockStrongholdGeneration extends ConfigurationPart {
        @Comments("Enable Bedrock-style random stronghold distribution (default: false)")
        public boolean enabled = false;
        @Comments("Average distance between strongholds in chunks (default: 48)")
        public int spacing = 48;
        @Comments("Minimum distance between strongholds in chunks, must be less than spacing (default: 12)")
        public int separation = 12;
    }

    @Comments("Syncmatica Protocol - Litematica schematic sharing between server and clients")
    public static SyncmaticaConfiguration syncmatica = new SyncmaticaConfiguration();

    @Comments("Servux Protocol - Structure bounding box overlay and entity data for MiniHUD/Litematica")
    public static ServuxConfiguration servux = new ServuxConfiguration();

    @Comments("Recipe Sync - Send recipe data to modded JEI clients via NeoForge/Fabric protocols")
    public static RecipeSyncConfiguration recipeSync = new RecipeSyncConfiguration();

    @Comments("Custom Recipe System - Define custom crafting, smelting, stonecutting recipes via config file")
    public static CustomRecipeConfiguration customRecipe = new CustomRecipeConfiguration();

    @PostProcess
    public void initProtocols() {
        org.leavesmc.leaves.protocol.core.LeavesProtocolManager.init();
    }

    @PostProcess
    public void registerCustomRecipes() {
        if (customRecipe.enabled) {
            org.bukkit.Bukkit.getPluginManager().registerEvents(
                    new org.bukkit.event.Listener() {
                        @org.bukkit.event.EventHandler
                        public void onServerLoad(org.bukkit.event.server.ServerLoadEvent event) {
                            cn.lunadeer.mc.deerfoliaplus.recipe.CustomRecipeManager.registerAllRecipes();
                        }
                    },
                    org.leavesmc.leaves.plugin.MinecraftInternalPlugin.INSTANCE
            );
        }
    }

}
