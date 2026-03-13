package org.leavesmc.leaves.protocol.syncmatica.exchange;

import net.minecraft.resources.Identifier;
import org.leavesmc.leaves.protocol.syncmatica.CommunicationManager;

public abstract class AbstractExchange implements Exchange {

    protected final ExchangeTarget partner;
    protected final CommunicationManager manager;
    private boolean finished = false;
    private boolean success = false;

    protected AbstractExchange(final ExchangeTarget partner, final CommunicationManager manager) {
        this.partner = partner;
        this.manager = manager;
    }

    @Override
    public ExchangeTarget getPartner() {
        return partner;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isSuccessful() {
        return success;
    }

    @Override
    public boolean checkPacket(final Identifier id) {
        return false;
    }

    @Override
    public void onClose() {
    }

    protected void close(final boolean success) {
        this.success = success;
        this.finished = true;
        manager.notifyClose(this);
    }

    protected void succeed() {
        close(true);
    }
}
