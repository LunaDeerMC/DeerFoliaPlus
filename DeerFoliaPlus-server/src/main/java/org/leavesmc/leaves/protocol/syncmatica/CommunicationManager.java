package org.leavesmc.leaves.protocol.syncmatica;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.leavesmc.leaves.protocol.syncmatica.exchange.AbstractExchange;
import org.leavesmc.leaves.protocol.syncmatica.exchange.DownloadExchange;
import org.leavesmc.leaves.protocol.syncmatica.exchange.Exchange;
import org.leavesmc.leaves.protocol.syncmatica.exchange.ExchangeTarget;
import org.leavesmc.leaves.protocol.syncmatica.exchange.FeatureExchange;
import org.leavesmc.leaves.protocol.syncmatica.exchange.ModifyExchangeServer;
import org.leavesmc.leaves.protocol.syncmatica.exchange.UploadExchange;
import org.leavesmc.leaves.protocol.syncmatica.exchange.VersionHandshakeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommunicationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Syncmatica");

    private static final Map<UUID, ExchangeTarget> EXCHANGE_TARGETS = new ConcurrentHashMap<>();

    private final SyncmaticManager syncmaticManager;
    private final FileStorage fileStorage;
    private final FeatureSet serverFeatures;

    public CommunicationManager(final SyncmaticManager syncmaticManager, final FileStorage fileStorage) {
        this.syncmaticManager = syncmaticManager;
        this.fileStorage = fileStorage;
        this.serverFeatures = FeatureSet.fromCollection(java.util.List.of(
            Feature.CORE,
            Feature.FEATURE,
            Feature.MODIFY,
            Feature.MESSAGE,
            Feature.QUOTA,
            Feature.CORE_EX
        ));
    }

    public static ExchangeTarget getExchangeTarget(final ServerPlayer player) {
        return EXCHANGE_TARGETS.get(player.getUUID());
    }

    public static GameProfile getGameProfile(final ExchangeTarget target) {
        return target.getPlayer().getGameProfile();
    }

    public void onPlayerJoin(final ServerPlayer player) {
        final ExchangeTarget exchangeTarget = new ExchangeTarget(player);
        EXCHANGE_TARGETS.put(player.getUUID(), exchangeTarget);

        syncmaticManager.getPlayerIdentifierProvider().updateName(
            player.getUUID(), player.getGameProfile().name()
        );

        startExchange(new VersionHandshakeServer(exchangeTarget, this));
    }

    public void onPlayerLeave(final ServerPlayer player) {
        final ExchangeTarget target = EXCHANGE_TARGETS.remove(player.getUUID());
        if (target != null) {
            for (final Exchange exchange : new ArrayList<>(target.getExchanges())) {
                exchange.onClose();
            }
            target.getExchanges().clear();
        }
    }

    public void onPacket(final ServerPlayer player, final Identifier id, final FriendlyByteBuf packetBuf) {
        final ExchangeTarget target = EXCHANGE_TARGETS.get(player.getUUID());
        if (target == null) {
            return;
        }

        for (final Exchange exchange : new ArrayList<>(target.getExchanges())) {
            if (exchange.checkPacket(id)) {
                exchange.handle(id, packetBuf);
                if (exchange.isFinished()) {
                    target.getExchanges().remove(exchange);
                }
                return;
            }
        }

        handleGenericPacket(target, id, packetBuf);
    }

    private void handleGenericPacket(final ExchangeTarget target, final Identifier id,
                                      final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.REGISTER_METADATA.identifier)) {
            handleRegisterMetadata(target, packetBuf);
        } else if (id.equals(PacketType.CANCEL_SHARE.identifier)) {
            handleCancelShare(target, packetBuf);
        } else if (id.equals(PacketType.REQUEST_LITEMATIC.identifier)) {
            handleRequestLitematic(target, packetBuf);
        } else if (id.equals(PacketType.REMOVE_SYNCMATIC.identifier)) {
            handleRemove(target, packetBuf);
        } else if (id.equals(PacketType.MODIFY_REQUEST.identifier)) {
            handleModifyRequest(target, packetBuf);
        } else if (id.equals(PacketType.FEATURE_REQUEST.identifier)) {
            handleFeatureRequest(target, packetBuf);
        }
    }

    private void handleRegisterMetadata(final ExchangeTarget target, final FriendlyByteBuf packetBuf) {
        final UUID syncmaticaId = packetBuf.readUUID();
        final String fileName = packetBuf.readUtf(32767);
        final String hash = packetBuf.readUtf(32767);

        final String dimensionId = packetBuf.readUtf(32767);
        final BlockPos position = packetBuf.readBlockPos();
        final ServerPosition origin = new ServerPosition(position, dimensionId);

        final ServerPlacement placement = new ServerPlacement(syncmaticaId, fileName, hash, origin);
        placement.setOwner(syncmaticManager.getPlayerIdentifierProvider().createOrGet(target));

        if (syncmaticManager.getPlacement(syncmaticaId) != null) {
            sendMessage(target, MessageType.ERROR, "A placement with that ID already exists.");
            return;
        }

        if (fileStorage.hasFile(placement)) {
            placement.setLocalState(LocalLitematicState.LOCAL_LITEMATIC_PRESENT);
            syncmaticManager.addPlacement(placement);
            broadcastPlacement(placement);
            confirmPlacement(target, placement);
        } else {
            placement.setLocalState(LocalLitematicState.DOWNLOADING_LITEMATIC);
            syncmaticManager.addPlacement(placement);
            startExchange(new DownloadExchange(placement, target, this, fileStorage));
        }
    }

    private void handleCancelShare(final ExchangeTarget target, final FriendlyByteBuf packetBuf) {
        final UUID syncmaticaId = packetBuf.readUUID();
        final ServerPlacement placement = syncmaticManager.getPlacement(syncmaticaId);
        if (placement == null) {
            return;
        }
        if (placement.getLocalState() == LocalLitematicState.DOWNLOADING_LITEMATIC) {
            syncmaticManager.removePlacement(placement);
        }
    }

    private void handleRequestLitematic(final ExchangeTarget target, final FriendlyByteBuf packetBuf) {
        final UUID syncmaticaId = packetBuf.readUUID();
        final ServerPlacement placement = syncmaticManager.getPlacement(syncmaticaId);
        if (placement == null || placement.getLocalState() != LocalLitematicState.LOCAL_LITEMATIC_PRESENT) {
            return;
        }
        startExchange(new UploadExchange(placement, target, this, fileStorage));
    }

    private void handleRemove(final ExchangeTarget target, final FriendlyByteBuf packetBuf) {
        final UUID syncmaticaId = packetBuf.readUUID();
        final ServerPlacement placement = syncmaticManager.getPlacement(syncmaticaId);
        if (placement == null) {
            return;
        }
        syncmaticManager.removePlacement(placement);
        broadcastRemoval(placement);
    }

    private void handleModifyRequest(final ExchangeTarget target, final FriendlyByteBuf packetBuf) {
        final UUID syncmaticaId = packetBuf.readUUID();
        final ServerPlacement placement = syncmaticManager.getPlacement(syncmaticaId);
        if (placement == null) {
            sendDenyModify(target, syncmaticaId);
            return;
        }
        if (!target.isFeature(Feature.MODIFY)) {
            sendDenyModify(target, syncmaticaId);
            return;
        }
        placement.setLastModifiedBy(syncmaticManager.getPlayerIdentifierProvider().createOrGet(target));
        startExchange(new ModifyExchangeServer(placement, target, this));
    }

    private void handleFeatureRequest(final ExchangeTarget target, final FriendlyByteBuf packetBuf) {
        final String featureString = packetBuf.readUtf(32767);
        final FeatureSet clientFeatures = FeatureSet.fromString(featureString);
        final FeatureSet agreedFeatures = serverFeatures.intersect(clientFeatures);
        target.setFeatureSet(agreedFeatures);

        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(serverFeatures.toString());
        target.sendPacket(PacketType.FEATURE.identifier, buf);
        buf.release();
    }

    private void sendDenyModify(final ExchangeTarget target, final UUID syncmaticaId) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUUID(syncmaticaId);
        target.sendPacket(PacketType.MODIFY_REQUEST_DENY.identifier, buf);
        buf.release();
    }

    public void onVersionHandshakeComplete(final ExchangeTarget target) {
        startExchange(new FeatureExchange(target, this, serverFeatures));
    }

    public void onFeatureExchangeComplete(final ExchangeTarget target) {
        for (final ServerPlacement placement : syncmaticManager.getAll()) {
            if (placement.getLocalState() == LocalLitematicState.LOCAL_LITEMATIC_PRESENT) {
                sendPlacement(target, placement);
            }
        }

        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        target.sendPacket(PacketType.CONFIRM_USER.identifier, buf);
        buf.release();
    }

    public void onDownloadComplete(final ServerPlacement placement, final ExchangeTarget source) {
        broadcastPlacement(placement);
        confirmPlacement(source, placement);
    }

    public void onModifyComplete(final ServerPlacement placement, final ExchangeTarget source) {
        syncmaticManager.updatePlacement(placement);
        broadcastModification(placement);
    }

    public void notifyClose(final Exchange exchange) {
        // Exchange closed, cleanup if needed
    }

    private void startExchange(final Exchange exchange) {
        final ExchangeTarget partner = exchange.getPartner();
        partner.getExchanges().add(exchange);
        exchange.init();
    }

    private void broadcastPlacement(final ServerPlacement placement) {
        for (final ExchangeTarget target : EXCHANGE_TARGETS.values()) {
            if (target.getFeatureSet().hasFeature(Feature.CORE)) {
                sendPlacement(target, placement);
            }
        }
    }

    private void broadcastRemoval(final ServerPlacement placement) {
        for (final ExchangeTarget target : EXCHANGE_TARGETS.values()) {
            if (target.getFeatureSet().hasFeature(Feature.CORE)) {
                final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                buf.writeUUID(placement.getId());
                target.sendPacket(PacketType.REMOVE_SYNCMATIC.identifier, buf);
                buf.release();
            }
        }
    }

    private void broadcastModification(final ServerPlacement placement) {
        for (final ExchangeTarget target : EXCHANGE_TARGETS.values()) {
            if (target.getFeatureSet().hasFeature(Feature.MODIFY)) {
                sendModification(target, placement);
            }
        }
    }

    private void sendPlacement(final ExchangeTarget target, final ServerPlacement placement) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUUID(placement.getId());
        buf.writeUtf(placement.getFileName());
        buf.writeUtf(placement.getHash());
        buf.writeUtf(placement.getOrigin().getDimensionId());
        buf.writeBlockPos(placement.getOrigin().getBlockPosition());
        target.sendPacket(PacketType.REGISTER_METADATA.identifier, buf);
        buf.release();
    }

    private void sendModification(final ExchangeTarget target, final ServerPlacement placement) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUUID(placement.getId());
        buf.writeUtf(placement.getOrigin().getDimensionId());
        buf.writeBlockPos(placement.getOrigin().getBlockPosition());

        final Collection<SubRegionPlacementModification> modifications = placement.getSubRegionPlacementModifications();
        buf.writeInt(modifications.size());
        for (final SubRegionPlacementModification mod : modifications) {
            buf.writeUtf(mod.getName());
            buf.writeBlockPos(mod.getPosition());
            buf.writeUtf(mod.getRotation());
            buf.writeUtf(mod.getMirror());
        }

        target.sendPacket(PacketType.MODIFY.identifier, buf);
        buf.release();
    }

    private void confirmPlacement(final ExchangeTarget target, final ServerPlacement placement) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUUID(placement.getId());
        target.sendPacket(PacketType.CONFIRM_USER.identifier, buf);
        buf.release();
    }

    public void sendMessage(final ExchangeTarget target, final MessageType type, final String message) {
        if (!target.isFeature(Feature.MESSAGE)) {
            return;
        }
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(type.toString());
        buf.writeUtf(message);
        target.sendPacket(PacketType.MESSAGE.identifier, buf);
        buf.release();
    }
}
