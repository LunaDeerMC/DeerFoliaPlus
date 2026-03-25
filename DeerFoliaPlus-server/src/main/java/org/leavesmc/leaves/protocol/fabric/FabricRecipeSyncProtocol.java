package org.leavesmc.leaves.protocol.fabric;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.core.ProtocolUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the Fabric API recipe sync protocol (fabric:recipe_sync).
 * <p>
 * Since Minecraft 1.21.2, vanilla no longer syncs full recipe data to clients.
 * Fabric API provides a recipe sync mechanism via the {@code fabric:recipe_sync} payload,
 * which JEI on Fabric listens for via {@code ClientRecipeSynchronizedEvent}.
 * <p>
 * Wire format (matches Fabric API's RecipeSyncPayloadS2C):
 * <pre>
 *   VarInt(entryCount)
 *   for each entry:
 *     Identifier(serializerKey)     — from RECIPE_SERIALIZER registry
 *     VarInt(recipeCount)
 *     for each recipe:
 *       ResourceKey(recipeId)       — written as Identifier (location only)
 *       Recipe(data)                — encoded by serializer.streamCodec()
 * </pre>
 */
@LeavesProtocol.Register(namespace = "fabric")
public class FabricRecipeSyncProtocol implements LeavesProtocol {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier RECIPE_SYNC_ID =
            Identifier.fromNamespaceAndPath("fabric", "recipe_sync");

    @Override
    public boolean isActive() {
        return DeerFoliaPlusConfiguration.recipeSync.fabric;
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerJoin(ServerPlayer player) {
        if (!DeerFoliaPlusConfiguration.recipeSync.fabric) return;
        sendRecipes(player);
    }

    @ProtocolHandler.ReloadDataPack
    public static void onDatapackReload() {
        if (!DeerFoliaPlusConfiguration.recipeSync.fabric) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendRecipes(player);
        }
    }

    @SuppressWarnings("unchecked")
    private static void sendRecipes(ServerPlayer player) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        RecipeMap recipeMap = server.getRecipeManager().recipes;

        // Group recipes by their serializer
        Map<RecipeSerializer<?>, List<RecipeHolder<?>>> grouped = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipeMap.values()) {
            RecipeSerializer<?> serializer = holder.value().getSerializer();
            grouped.computeIfAbsent(serializer, k -> new ArrayList<>()).add(holder);
        }

        ProtocolUtils.sendBytebufPacket(player, RECIPE_SYNC_ID, buf -> {
            // Write list size (number of entries / serializer groups)
            buf.writeVarInt(grouped.size());

            for (Map.Entry<RecipeSerializer<?>, List<RecipeHolder<?>>> entry : grouped.entrySet()) {
                RecipeSerializer<?> serializer = entry.getKey();
                List<RecipeHolder<?>> recipes = entry.getValue();

                // Write serializer identifier
                Identifier serializerKey = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
                buf.writeIdentifier(serializerKey);

                // Write recipe count
                buf.writeVarInt(recipes.size());

                // Write each recipe
                StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> streamCodec =
                        (StreamCodec<RegistryFriendlyByteBuf, Recipe<?>>) (StreamCodec<?, ?>) serializer.streamCodec();
                for (RecipeHolder<?> holder : recipes) {
                    buf.writeResourceKey(holder.id());
                    streamCodec.encode(buf, holder.value());
                }
            }
        });

        int totalRecipes = grouped.values().stream().mapToInt(List::size).sum();
        LOGGER.debug("Sent {} recipes ({} serializer groups) via Fabric protocol to {}",
                totalRecipes, grouped.size(), player.getGameProfile().name());
    }
}
