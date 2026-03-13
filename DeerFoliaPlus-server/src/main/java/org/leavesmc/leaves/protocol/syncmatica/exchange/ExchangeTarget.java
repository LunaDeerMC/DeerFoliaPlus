package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.leavesmc.leaves.protocol.core.ProtocolUtils;
import org.leavesmc.leaves.protocol.syncmatica.FeatureSet;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaPayload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExchangeTarget {

    private final ServerPlayer player;
    private FeatureSet features;
    private final List<Exchange> exchanges = new ArrayList<>();
    private boolean persistentTarget = false;

    public ExchangeTarget(final ServerPlayer player) {
        this.player = player;
        this.features = FeatureSet.empty();
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public FeatureSet getFeatureSet() {
        return features;
    }

    public void setFeatureSet(final FeatureSet features) {
        this.features = features;
    }

    public boolean isPersistent() {
        return persistentTarget;
    }

    public void setPersistent(final boolean persistent) {
        this.persistentTarget = persistent;
    }

    public void sendPacket(final Identifier id, final FriendlyByteBuf packetBuf) {
        final byte[] bytes = new byte[packetBuf.readableBytes()];
        packetBuf.readBytes(bytes);
        ProtocolUtils.sendPayloadPacket(player, new SyncmaticaPayload(id, bytes));
    }

    public Collection<Exchange> getExchanges() {
        return exchanges;
    }

    public Exchange getExchange(final PacketType type) {
        for (final Exchange exchange : exchanges) {
            if (exchange.checkPacket(type.identifier)) {
                return exchange;
            }
        }
        return null;
    }

    public boolean isFeature(final org.leavesmc.leaves.protocol.syncmatica.Feature feature) {
        return features.hasFeature(feature);
    }
}
