package org.leavesmc.leaves.protocol.servux;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

// Powered by Servux(https://github.com/sakura-ryoko/servux)

/**
 * Network packet splitter code from QuickCarpet by skyrising
 *
 * @author skyrising
 * <p>
 * Updated by Sakura to work with newer versions by changing the Reading Session keys,
 * and using the HANDLER interface to send packets via the Payload system
 * <p>
 * Move to Leaves by violetc
 */
public class PacketSplitter {

    public static final int MAX_TOTAL_PER_PACKET_S2C = 1048576;
    public static final int MAX_PAYLOAD_PER_PACKET_S2C = MAX_TOTAL_PER_PACKET_S2C - 5;
    public static final int MAX_TOTAL_PER_PACKET_C2S = 32767;
    public static final int MAX_PAYLOAD_PER_PACKET_C2S = MAX_TOTAL_PER_PACKET_C2S - 5;
    /** Takes ownership of packet, including when encoding or sending fails. */
    public static boolean send(IPacketSplitterHandler handler, FriendlyByteBuf packet, ServerPlayer player) {
        return send(handler, packet, player, MAX_PAYLOAD_PER_PACKET_S2C);
    }

    public static boolean sendNbt(IPacketSplitterHandler handler, CompoundTag nbt, ServerPlayer player) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.writeNbt(nbt);
        } catch (RuntimeException | Error failure) {
            packet.release();
            throw failure;
        }
        return send(handler, packet, player);
    }

    private static boolean send(IPacketSplitterHandler handler, FriendlyByteBuf packet, ServerPlayer player, int payloadLimit) {
        try {
            if (payloadLimit <= 0 || payloadLimit > MAX_PAYLOAD_PER_PACKET_S2C) {
                throw new IllegalArgumentException("Invalid payload limit");
            }
            int len = packet.writerIndex();
            packet.readerIndex(0);
            int offset = 0;
            do {
                int thisLen = Math.min(len - offset, payloadLimit);
                FriendlyByteBuf fragment = new FriendlyByteBuf(Unpooled.buffer(thisLen + 5));
                try {
                    if (offset == 0) {
                        fragment.writeVarInt(len);
                    }
                    fragment.writeBytes(packet, thisLen);
                    handler.encode(player, fragment);
                } finally {
                    fragment.release();
                }
                offset += thisLen;
            } while (offset < len);
            return true;
        } finally {
            packet.release();
        }
    }

    public interface IPacketSplitterHandler {
        /** The fragment is borrowed for this call only; copy its bytes before retaining a payload. */
        void encode(ServerPlayer player, FriendlyByteBuf buf);
    }
}
