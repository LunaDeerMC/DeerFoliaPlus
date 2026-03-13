package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;
import org.leavesmc.leaves.protocol.syncmatica.Feature;
import org.leavesmc.leaves.protocol.syncmatica.FeatureSet;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;

public class FeatureExchange extends AbstractExchange {

    private final FeatureSet serverFeatures;

    public FeatureExchange(final ExchangeTarget partner, final CommunicationManager manager, final FeatureSet serverFeatures) {
        super(partner, manager);
        this.serverFeatures = serverFeatures;
    }

    @Override
    public boolean checkPacket(final Identifier id) {
        return id.equals(PacketType.FEATURE_REQUEST.identifier) || id.equals(PacketType.FEATURE.identifier);
    }

    @Override
    public void init() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(serverFeatures.toString());
        partner.sendPacket(PacketType.FEATURE.identifier, buf);
        buf.release();
    }

    @Override
    public void handle(final Identifier id, final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.FEATURE.identifier) || id.equals(PacketType.FEATURE_REQUEST.identifier)) {
            final String featureString = packetBuf.readUtf(32767);
            final FeatureSet clientFeatures = FeatureSet.fromString(featureString);
            final FeatureSet agreedFeatures = serverFeatures.intersect(clientFeatures);
            partner.setFeatureSet(agreedFeatures);

            if (id.equals(PacketType.FEATURE_REQUEST.identifier)) {
                final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                buf.writeUtf(serverFeatures.toString());
                partner.sendPacket(PacketType.FEATURE.identifier, buf);
                buf.release();
            }

            manager.onFeatureExchangeComplete(partner);
            succeed();
        }
    }
}
