package org.leavesmc.leaves.protocol.syncmatica.exchange;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;
import org.leavesmc.leaves.protocol.syncmatica.FileStorage;
import org.leavesmc.leaves.protocol.syncmatica.LocalLitematicState;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.ServerPlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class DownloadExchange extends AbstractExchange {

    private static final Logger LOGGER = LoggerFactory.getLogger("Syncmatica");

    private final ServerPlacement placement;
    private final FileStorage fileStorage;
    private File downloadFile;
    private OutputStream outputStream;
    private MessageDigest md5;
    private int totalBytes = 0;

    public DownloadExchange(final ServerPlacement placement, final ExchangeTarget partner,
                            final CommunicationManager manager, final FileStorage fileStorage) {
        super(partner, manager);
        this.placement = placement;
        this.fileStorage = fileStorage;
    }

    @Override
    public boolean checkPacket(final Identifier id) {
        return id.equals(PacketType.SEND_LITEMATIC.identifier)
            || id.equals(PacketType.FINISHED_LITEMATIC.identifier)
            || id.equals(PacketType.CANCEL_LITEMATIC.identifier);
    }

    @Override
    public void init() {
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            LOGGER.error("Failed to create MD5 digest", e);
            close(false);
            return;
        }

        downloadFile = fileStorage.createTempFile(placement);
        if (downloadFile == null) {
            close(false);
            return;
        }

        try {
            outputStream = new FileOutputStream(downloadFile);
        } catch (final IOException e) {
            LOGGER.error("Failed to open download file", e);
            close(false);
            return;
        }

        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUUID(placement.getId());
        partner.sendPacket(PacketType.REQUEST_LITEMATIC.identifier, buf);
        buf.release();
    }

    @Override
    public void handle(final Identifier id, final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.SEND_LITEMATIC.identifier)) {
            handleChunk(packetBuf);
        } else if (id.equals(PacketType.FINISHED_LITEMATIC.identifier)) {
            handleFinish(packetBuf);
        } else if (id.equals(PacketType.CANCEL_LITEMATIC.identifier)) {
            handleCancel();
        }
    }

    private void handleChunk(final FriendlyByteBuf packetBuf) {
        final UUID packetId = packetBuf.readUUID();
        if (!packetId.equals(placement.getId())) {
            return;
        }
        final int size = packetBuf.readInt();
        final byte[] data = new byte[size];
        packetBuf.readBytes(data);

        if (DeerFoliaPlusConfiguration.syncmatica.useQuota
            && totalBytes + size > DeerFoliaPlusConfiguration.syncmatica.quotaLimit) {
            LOGGER.warn("Syncmatica download exceeded quota limit for placement {}", placement.getId());
            cancel();
            return;
        }

        totalBytes += size;
        md5.update(data);

        try {
            outputStream.write(data);
        } catch (final IOException e) {
            LOGGER.error("Failed to write download chunk", e);
            cancel();
        }

        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUUID(placement.getId());
        partner.sendPacket(PacketType.RECEIVED_LITEMATIC.identifier, buf);
        buf.release();
    }

    private void handleFinish(final FriendlyByteBuf packetBuf) {
        final UUID packetId = packetBuf.readUUID();
        if (!packetId.equals(placement.getId())) {
            return;
        }

        try {
            outputStream.flush();
            outputStream.close();
        } catch (final IOException e) {
            LOGGER.error("Failed to close download file", e);
            close(false);
            return;
        }

        final String calculatedHash = getHashFromDigest(md5);
        if (calculatedHash.equals(placement.getHash())) {
            if (fileStorage.finalizeFile(placement, downloadFile)) {
                placement.setLocalState(LocalLitematicState.LOCAL_LITEMATIC_PRESENT);
                manager.onDownloadComplete(placement, partner);
                succeed();
            } else {
                LOGGER.error("Failed to finalize file for {}", placement.getId());
                close(false);
            }
        } else {
            LOGGER.error("Hash mismatch for {}: expected {} got {}", placement.getId(), placement.getHash(), calculatedHash);
            if (downloadFile.exists()) {
                downloadFile.delete();
            }
            close(false);
        }
    }

    private void handleCancel() {
        cleanup();
        close(false);
    }

    private void cancel() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUUID(placement.getId());
        partner.sendPacket(PacketType.CANCEL_LITEMATIC.identifier, buf);
        buf.release();
        cleanup();
        close(false);
    }

    private void cleanup() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (final IOException ignored) {
        }
        if (downloadFile != null && downloadFile.exists()) {
            downloadFile.delete();
        }
    }

    @Override
    public void onClose() {
        cleanup();
    }

    private static String getHashFromDigest(final MessageDigest md5) {
        final byte[] digest = md5.digest();
        final StringBuilder sb = new StringBuilder();
        for (final byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
