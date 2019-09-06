package de.codesourcery.logreceiver.events;

import de.codesourcery.logreceiver.entity.Host;
import org.apache.commons.lang3.Validate;

public final class HostDeletedEvent implements IHostEvent
{
    public final Host deletedHost;

    public HostDeletedEvent(Host deletedHost)
    {
        Validate.notNull( deletedHost, "deletedHost must not be null" );
        this.deletedHost = deletedHost.copy();
    }
}
