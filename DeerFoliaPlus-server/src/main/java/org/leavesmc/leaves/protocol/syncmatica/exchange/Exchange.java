package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public interface Exchange {

    boolean checkPacket(Identifier id);

    void handle(Identifier id, FriendlyByteBuf packetBuf);

    boolean isFinished();

    void init();

    void onClose();

    boolean isSuccessful();

    ExchangeTarget getPartner();
}
