package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;
import org.leavesmc.leaves.protocol.syncmatica.PacketType;
import org.leavesmc.leaves.protocol.syncmatica.ServerPlacement;
import org.leavesmc.leaves.protocol.syncmatica.ServerPosition;
import org.leavesmc.leaves.protocol.syncmatica.SubRegionPlacementModification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class ModifyExchangeServer extends AbstractExchange {

    private static final Logger LOGGER = LoggerFactory.getLogger("Syncmatica");

    private final ServerPlacement placement;
    private boolean accepted = false;

    public ModifyExchangeServer(final ServerPlacement placement, final ExchangeTarget partner,
                                 final CommunicationManager manager) {
        super(partner, manager);
        this.placement = placement;
    }

    @Override
    public boolean checkPacket(final Identifier id) {
        return id.equals(PacketType.MODIFY.identifier)
            || id.equals(PacketType.MODIFY_FINISH.identifier);
    }

    @Override
    public void init() {
        accepted = true;
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUUID(placement.getId());
        partner.sendPacket(PacketType.MODIFY_REQUEST_ACCEPT.identifier, buf);
        buf.release();
    }

    @Override
    public void handle(final Identifier id, final FriendlyByteBuf packetBuf) {
        if (id.equals(PacketType.MODIFY.identifier)) {
            handleModify(packetBuf);
        } else if (id.equals(PacketType.MODIFY_FINISH.identifier)) {
            handleFinish(packetBuf);
        }
    }

    private void handleModify(final FriendlyByteBuf packetBuf) {
        final UUID packetId = packetBuf.readUUID();
        if (!packetId.equals(placement.getId())) {
            return;
        }

        final String dimensionId = packetBuf.readUtf(32767);
        final BlockPos position = packetBuf.readBlockPos();

        final int subregionCount = packetBuf.readInt();
        final Collection<SubRegionPlacementModification> modifications = new ArrayList<>();
        for (int i = 0; i < subregionCount; i++) {
            final String name = packetBuf.readUtf(32767);
            final BlockPos subPos = packetBuf.readBlockPos();
            final String rotation = packetBuf.readUtf(32767);
            final String mirror = packetBuf.readUtf(32767);
            modifications.add(new SubRegionPlacementModification(name, subPos, rotation, mirror));
        }

        placement.move(dimensionId, position);
        placement.setSubRegionPlacementModifications(modifications);
    }

    private void handleFinish(final FriendlyByteBuf packetBuf) {
        final UUID packetId = packetBuf.readUUID();
        if (!packetId.equals(placement.getId())) {
            return;
        }
        manager.onModifyComplete(placement, partner);
        succeed();
    }

    @Override
    public void onClose() {
        if (!isFinished()) {
            close(false);
        }
    }
}
