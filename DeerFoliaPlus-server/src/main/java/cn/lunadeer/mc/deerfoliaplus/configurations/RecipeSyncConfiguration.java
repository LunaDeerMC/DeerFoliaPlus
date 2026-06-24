package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.Comments;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;

public class RecipeSyncConfiguration extends ConfigurationPart {
    @Comments("NeoForge Recipe Sync - Send recipe data to NeoForge JEI clients via neoforge:recipe_content protocol")
    public boolean neoforge = true;

    @Comments("Fabric Recipe Sync - Send recipe data to Fabric JEI clients via fabric:recipe_sync protocol")
    public boolean fabric = true;

    @Comments("Skip Fabric Recipe Sync for players connecting via ViaVersion/ViaBackwards with non-native protocol versions")
    public boolean skipFabricOnViaNonNative = true;
}