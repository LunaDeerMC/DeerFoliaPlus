package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;
import org.leavesmc.leaves.protocol.syncmatica.FileStorage;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.ServerPlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class UploadExchange extends AbstractExchange {

    private static final Logger LOGGER = LoggerFactory.getLogger("Syncmatica");
    private static final int CHUNK_SIZE = 32000;

    private final ServerPlacement placement;
    private final FileStorage fileStorage;
    private InputStream inputStream;
    private boolean uploadStarted = false;

    public UploadExchange(final ServerPlacement placement, final ExchangeTarget partner,
                          final CommunicationManager manager, final FileStorage fileStorage) {
        super(partner, manager);
        this.placement = placement;
        this.fileStorage = fileStorage;
    }

    @Override
    public boolean checkPacket(final Identifier id) {
        return id.equals(PacketType.RECEIVED_LITEMATIC.identifier)
            || id.equals(PacketType.CANCEL_LITEMATIC.identifier);
    }

    @Override
    public void init() {
        final File file = fileStorage.getFile(placement);
        if (file == null || !file.exists()) {
            LOGGER.error("File not found for placement {}", placement.getId());
            close(false);
            return;
        }

        try {
            inputStream = new FileInputStream(file);
        } catch (final IOException e) {
            LOGGER.error("Failed to open file for upload", e);
            close(false);
            return;
        }

        uploadStarted = true;
        sendChunk();
    }

    @Override
    public void handle(final Identifier id, final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.RECEIVED_LITEMATIC.identifier)) {
            final UUID packetId = packetBuf.readUUID();
            if (packetId.equals(placement.getId())) {
                sendChunk();
            }
        } else if (id.equals(PacketType.CANCEL_LITEMATIC.identifier)) {
            cleanup();
            close(false);
        }
    }

    private void sendChunk() {
        try {
            final byte[] data = new byte[CHUNK_SIZE];
            final int bytesRead = inputStream.read(data);

            if (bytesRead == -1) {
                inputStream.close();
                final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                buf.writeUUID(placement.getId());
                partner.sendPacket(PacketType.FINISHED_LITEMATIC.identifier, buf);
                buf.release();
                succeed();
                return;
            }

            final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeUUID(placement.getId());
            buf.writeInt(bytesRead);
            buf.writeBytes(data, 0, bytesRead);
            partner.sendPacket(PacketType.SEND_LITEMATIC.identifier, buf);
            buf.release();
        } catch (final IOException e) {
            LOGGER.error("Failed to read file for upload", e);
            cleanup();
            close(false);
        }
    }

    private void cleanup() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (final IOException ignored) {
        }
    }

    @Override
    public void onClose() {
        cleanup();
    }
}
