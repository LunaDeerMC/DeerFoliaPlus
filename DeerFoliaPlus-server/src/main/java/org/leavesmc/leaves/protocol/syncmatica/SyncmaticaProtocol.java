package org.leavesmc.leaves.protocol.syncmatica;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@LeavesProtocol.Register(namespace = "syncmatica")
public class SyncmaticaProtocol implements LeavesProtocol {

    public static final String PROTOCOL_ID = "syncmatica";
    public static final String VERSION = "0.3.11-pre";
    private static final Logger LOGGER = LoggerFactory.getLogger("Syncmatica");

    private static CommunicationManager communicationManager;
    private static SyncmaticManager syncmaticManager;
    private static FileStorage fileStorage;

    @Override
    public boolean isActive() {
        return DeerFoliaPlusConfiguration.syncmatica.enable;
    }

    @ProtocolHandler.Init
    public static void init() {
        final File baseDir = new File("syncmatica");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        fileStorage = new FileStorage(baseDir);
        syncmaticManager = new SyncmaticManager(baseDir);
        communicationManager = new CommunicationManager(syncmaticManager, fileStorage);

        syncmaticManager.load();
        fileStorage.cleanTemp();

        LOGGER.info("Syncmatica protocol initialized");
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerJoin(final ServerPlayer player) {
        if (communicationManager != null) {
            communicationManager.onPlayerJoin(player);
        }
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLeave(final ServerPlayer player) {
        if (communicationManager != null) {
            communicationManager.onPlayerLeave(player);
        }
    }

    @ProtocolHandler.PayloadReceiver(payload = SyncmaticaPayload.class)
    public static void onPayloadReceive(final ServerPlayer player, final SyncmaticaPayload payload) {
        if (communicationManager != null) {
            final FriendlyByteBuf buf = payload.toFriendlyByteBuf();
            try {
                communicationManager.onPacket(player, payload.id(), buf);
            } finally {
                buf.release();
            }
        }
    }

    public static CommunicationManager getCommunicationManager() {
        return communicationManager;
    }

    public static SyncmaticManager getSyncmaticManager() {
        return syncmaticManager;
    }

    public static FileStorage getFileStorage() {
        return fileStorage;
    }
}
