package org.leavesmc.leaves.protocol.fabric;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.leavesmc.leaves.protocol.core.IdentifierSelector;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.core.ProtocolUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the Fabric API recipe sync protocol (fabric:recipe_sync).
 * <p>
 * Wire format verified against Fabric API 0.153.0+26.1.2
 * (fabric-recipe-api-v1 9.0.16, class ClientboundRecipeSyncPayload):
 * <pre>
 *   S2C fabric:recipe_sync:
 *     VarInt(entryCount)
 *     for each entry:
 *       Identifier(serializerKey)
 *       VarInt(recipeCount)
 *       for each recipe:
 *         ResourceKey(recipeId)
 *         Recipe(data) via serializer.streamCodec()
 *         ** NO dispatch VarInt serializer ID **
 * </pre>
 * <p>
 * C2S fabric:recipe_sync/supported_serializers:
 * <pre>
 *   VarInt(count)
 *   for each:
 *     Identifier(serializerId)
 * </pre>
 */
@LeavesProtocol.Register(namespace = "fabric")
public class FabricRecipeSyncProtocol implements LeavesProtocol {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier RECIPE_SYNC_ID =
            Identifier.fromNamespaceAndPath("fabric", "recipe_sync");

    private static final Map<UUID, Set<Identifier>> SUPPORTED_SERIALIZERS = new ConcurrentHashMap<>();

    @Override
    public boolean isActive() {
        return DeerFoliaPlusConfiguration.recipeSync.fabric;
    }

    @ProtocolHandler.BytebufReceiver(key = "fabric:recipe_sync/supported_serializers")
    public static void onSupportedSerializers(IdentifierSelector selector, FriendlyByteBuf buf) {
        ServerPlayer player = selector.player();
        if (player == null || !DeerFoliaPlusConfiguration.recipeSync.fabric) return;

        try {
            int count = buf.readVarInt();
            Set<Identifier> supported = new HashSet<>();
            for (int i = 0; i < count; i++) {
                supported.add(buf.readIdentifier());
            }
            SUPPORTED_SERIALIZERS.put(player.getUUID(), supported);
            LOGGER.debug("Player {} declared {} supported recipe serializers",
                    player.getGameProfile().name(), supported.size());

            sendRecipes(player);
        } catch (Exception e) {
            LOGGER.warn("Failed to read supported serializers from {}: {}",
                    player.getGameProfile().name(), e.getMessage(), e);
        }
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerJoin(ServerPlayer player) {
        if (!DeerFoliaPlusConfiguration.recipeSync.fabric) return;
        SUPPORTED_SERIALIZERS.remove(player.getUUID());
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLeave(ServerPlayer player) {
        SUPPORTED_SERIALIZERS.remove(player.getUUID());
    }

    @ProtocolHandler.ReloadDataPack
    public static void onDatapackReload() {
        if (!DeerFoliaPlusConfiguration.recipeSync.fabric) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SUPPORTED_SERIALIZERS.containsKey(player.getUUID())) {
                sendRecipes(player);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void sendRecipes(ServerPlayer player) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        Set<Identifier> supported = SUPPORTED_SERIALIZERS.get(player.getUUID());
        if (supported == null || supported.isEmpty()) {
            LOGGER.debug("Skipping fabric recipe sync for {}: no supported serializers declared",
                    player.getGameProfile().name());
            return;
        }

        if (DeerFoliaPlusConfiguration.recipeSync.skipFabricOnViaNonNative && isViaNonNative(player)) {
            LOGGER.debug("Skipping fabric recipe sync for {}: ViaVersion non-native protocol",
                    player.getGameProfile().name());
            return;
        }

        RecipeMap recipeMap = server.getRecipeManager().recipes;

        Map<RecipeSerializer<?>, List<net.minecraft.world.item.crafting.RecipeHolder<?>>> grouped =
                new LinkedHashMap<>();
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : recipeMap.values()) {
            RecipeSerializer<?> serializer = holder.value().getSerializer();
            Identifier serializerKey = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
            if (serializerKey == null || !supported.contains(serializerKey)) {
                LOGGER.debug("Skipping serializer {} for {}: not in supported set",
                        serializerKey, player.getGameProfile().name());
                continue;
            }
            if (serializer.streamCodec() == null) {
                LOGGER.debug("Skipping recipe {} serializer {}: streamCodec is null",
                        holder.id(), serializerKey);
                continue;
            }
            grouped.computeIfAbsent(serializer, k -> new ArrayList<>()).add(holder);
        }

        if (grouped.isEmpty()) {
            LOGGER.debug("No recipes to send to {} after filtering by supported serializers",
                    player.getGameProfile().name());
            return;
        }

        ProtocolUtils.sendBytebufPacket(player, RECIPE_SYNC_ID, buf -> {
            buf.writeVarInt(grouped.size());

            for (Map.Entry<RecipeSerializer<?>, List<net.minecraft.world.item.crafting.RecipeHolder<?>>> entry :
                    grouped.entrySet()) {
                RecipeSerializer<?> serializer = entry.getKey();
                List<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes = entry.getValue();

                Identifier serializerKey = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
                buf.writeIdentifier(serializerKey);

                @SuppressWarnings("rawtypes")
                net.minecraft.network.codec.StreamCodec encoder = serializer.streamCodec();

                List<net.minecraft.world.item.crafting.RecipeHolder<?>> validRecipes = new ArrayList<>(recipes.size());
                for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : recipes) {
                    RegistryFriendlyByteBuf tempBuf = ProtocolUtils.decorate(Unpooled.buffer());
                    try {
                        tempBuf.writeResourceKey(holder.id());
                        encoder.encode(tempBuf, holder.value());
                        validRecipes.add(holder);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to encode recipe {} (serializer {}): {}",
                                holder.id(), serializerKey, e.getMessage());
                    } finally {
                        tempBuf.release();
                    }
                }

                buf.writeVarInt(validRecipes.size());
                for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : validRecipes) {
                    buf.writeResourceKey(holder.id());
                    encoder.encode(buf, holder.value());
                }
            }
        });

        int totalRecipes = grouped.values().stream().mapToInt(List::size).sum();
        LOGGER.debug("Sent {} recipes ({} serializer groups) via Fabric protocol to {}",
                totalRecipes, grouped.size(), player.getGameProfile().name());
    }

    private static boolean isViaNonNative(ServerPlayer player) {
        try {
            Class<?> viaApiClass = Class.forName("com.viaversion.viaversion.api.Via");
            Object connectionManager = viaApiClass.getMethod("getConnectionManager").invoke(null);
            Object connection = connectionManager.getClass()
                    .getMethod("getConnectedClient", UUID.class)
                    .invoke(connectionManager, player.getUUID());
            if (connection == null) return false;

            int playerProtocol = (int) connection.getClass()
                    .getMethod("getProtocolVersion").invoke(connection);
            int serverProtocol = net.minecraft.SharedConstants.getCurrentVersion().protocolVersion();
            return playerProtocol != serverProtocol;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Exception e) {
            LOGGER.debug("Failed to check ViaVersion status for {}: {}",
                    player.getGameProfile().name(), e.getMessage());
            return false;
        }
    }
}
