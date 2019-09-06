package de.codesourcery.logreceiver.events;

import de.codesourcery.logreceiver.entity.Host;

public final class HostUpdatedEvent implements IHostEvent
{
    public final Host oldHost;
    public final Host newHost;

    public HostUpdatedEvent(Host oldHost, Host newHost)
    {
        this.oldHost = oldHost.copy();
        this.newHost = newHost.copy();
    }
}
