package org.leavesmc.leaves.protocol.neoforge;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.core.ProtocolUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements the NeoForge recipe content sync protocol (neoforge:recipe_content).
 * <p>
 * Since Minecraft 1.21.2, full recipe data is no longer sent to clients via the vanilla
 * recipe update packet. NeoForge added a custom payload ({@code neoforge:recipe_content})
 * to sync recipe data to clients so that mods like JEI can display recipes.
 * <p>
 * This protocol replicates that payload format, allowing NeoForge JEI clients to
 * receive recipe data from a DeerFoliaPlus server.
 */
@LeavesProtocol.Register(namespace = "neoforge")
public class NeoForgeRecipeSyncProtocol implements LeavesProtocol {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier RECIPE_CONTENT_ID =
            Identifier.fromNamespaceAndPath("neoforge", "recipe_content");

    @Override
    public boolean isActive() {
        return DeerFoliaPlusConfiguration.recipeSync.neoforge;
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerJoin(ServerPlayer player) {
        if (!DeerFoliaPlusConfiguration.recipeSync.neoforge) return;
        sendRecipes(player);
    }

    @ProtocolHandler.ReloadDataPack
    public static void onDatapackReload() {
        if (!DeerFoliaPlusConfiguration.recipeSync.neoforge) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendRecipes(player);
        }
    }

    private static void sendRecipes(ServerPlayer player) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        RecipeMap recipeMap = server.getRecipeManager().recipes;

        List<RecipeHolder<?>> allRecipes = new ArrayList<>(recipeMap.values());
        Set<RecipeType<?>> recipeTypes = allRecipes.stream()
                .map(h -> h.value().getType())
                .collect(Collectors.toCollection(HashSet::new));

        // Encode using the same format as NeoForge's RecipeContentPayload.STREAM_CODEC:
        // 1. Set<RecipeType<?>> via ByteBufCodecs.registry(RECIPE_TYPE) + collection(HashSet::new)
        // 2. List<RecipeHolder<?>> via RecipeHolder.STREAM_CODEC + list()
        StreamCodec<RegistryFriendlyByteBuf, RecipeType<?>> recipeTypeCodec =
                ByteBufCodecs.registry(Registries.RECIPE_TYPE);
        ProtocolUtils.sendBytebufPacket(player, RECIPE_CONTENT_ID, buf -> {
            // Write recipe types as a set
            buf.writeVarInt(recipeTypes.size());
            for (RecipeType<?> type : recipeTypes) {
                recipeTypeCodec.encode(buf, type);
            }
            // Write recipes as a list
            buf.writeVarInt(allRecipes.size());
            for (RecipeHolder<?> holder : allRecipes) {
                RecipeHolder.STREAM_CODEC.encode(buf, holder);
            }
        });

        LOGGER.debug("Sent {} recipes ({} types) to {}", allRecipes.size(), recipeTypes.size(),
                player.getGameProfile().name());
    }
}
