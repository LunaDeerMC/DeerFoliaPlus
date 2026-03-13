package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol;

public class VersionHandshakeServer extends AbstractExchange {

    private static final String VERSION = SyncmaticaProtocol.VERSION;

    public VersionHandshakeServer(final ExchangeTarget partner, final CommunicationManager manager) {
        super(partner, manager);
    }

    @Override
    public boolean checkPacket(final Identifier id) {
        return id.equals(PacketType.REGISTER_VERSION.identifier);
    }

    @Override
    public void init() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(VERSION);
        partner.sendPacket(PacketType.REGISTER_VERSION.identifier, buf);
        buf.release();
    }

    @Override
    public void handle(final Identifier id, final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.REGISTER_VERSION.identifier)) {
            final String clientVersion = packetBuf.readUtf(32767);
            // Version negotiation - accept any version for now
            manager.onVersionHandshakeComplete(partner);
            succeed();
        }
    }
}
