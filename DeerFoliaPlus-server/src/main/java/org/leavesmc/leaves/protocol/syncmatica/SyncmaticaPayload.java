package org.leavesmc.leaves.protocol.syncmatica;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.leavesmc.leaves.protocol.core.LeavesCustomPayload;

public record SyncmaticaPayload(Identifier id, byte[] data) implements LeavesCustomPayload {

    @LeavesCustomPayload.Codec
    public static final StreamCodec<FriendlyByteBuf, SyncmaticaPayload> CODEC = CustomPacketPayload.codec(SyncmaticaPayload::write, SyncmaticaPayload::new);
    @LeavesCustomPayload.ID
    public static final Identifier PAYLOAD_ID = Identifier.fromNamespaceAndPath(SyncmaticaProtocol.PROTOCOL_ID, "main");

    public SyncmaticaPayload(final Identifier id, final FriendlyByteBuf buf) {
        this(id, readByteBufData(buf));
    }

    private SyncmaticaPayload(final FriendlyByteBuf buf) {
        this(buf.readIdentifier(), readByteBufData(buf));
    }

    private static byte[] readByteBufData(final FriendlyByteBuf buf) {
        final byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeIdentifier(id);
        buf.writeBytes(data);
    }

    public FriendlyByteBuf toFriendlyByteBuf() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBytes(data);
        return buf;
    }
}
