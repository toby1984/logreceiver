package de.codesourcery.logreceiver.events;

import de.codesourcery.logreceiver.entity.Host;
import org.apache.commons.lang3.Validate;

public final class HostAddedEvent implements IHostEvent
{
    public final Host newHost;

    public HostAddedEvent(Host newHost)
    {
        Validate.notNull( newHost, "newHost must not be null" );
        this.newHost = newHost.copy();
    }
}
