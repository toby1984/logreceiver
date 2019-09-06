package de.codesourcery.logreceiver.storage;

import de.codesourcery.logreceiver.entity.Configuration;
import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.events.HostAddedEvent;
import de.codesourcery.logreceiver.util.EventBus;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryHostIdManager implements IHostManager
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( InMemoryHostIdManager.class.getName() );

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    protected final Object LOCK = new Object();

    private long currentId = 1;

    // @GuardedBy( LOCK )
    protected final Map<Long, Host> hostsById = new HashMap<>();
    // @GuardedBy( LOCK )
    protected final Map<InetAddress,Host> hostsByIP = new HashMap<>();

    protected final Configuration config;

    private final EventBus eventBus;

    public InMemoryHostIdManager(Configuration config,EventBus eventBus) {
        this.config = config;
        this.eventBus = eventBus;
    }

    @Override
    public List<Host> getAllHosts()
    {
        synchronized (LOCK) {
            final List<Host> result = new ArrayList<>();
            for ( Host h : hostsById.values() ) {
                result.add( h.copy() );
            }
            return result;
        }
    }

    @Override
    public Host getHost(long id)
    {
        synchronized(LOCK)
        {
            final Host result = hostsById.get( id );
            return result == null ? null : result.copy();
        }
    }

    @Override
    public Host getHost(InetAddress address)
    {
        synchronized(LOCK)
        {
            return hostsByIP.get( address );
        }
    }

    @Override
    public final Host getOrCreateHost(InetAddress ip,  String hostname)
    {
        Host host;
        boolean isNewHost = false;
        synchronized (LOCK)
        {
            host = hostsByIP.get(ip);
            if ( host == null )
            {
                if ( LOG.isTraceEnabled() ) {
                    LOG.trace("getOrCreateHostId(): Generating ID for "+ip+" ("+hostname+")");
                }
                host = generateHost(ip,hostname);
                isNewHost = true;
                hostsByIP.put( ip, host );
                hostsById.put( host.id, host );
            }
        }
        if ( isNewHost )
        {
            eventBus.send( new HostAddedEvent( host ) );
        }
        return host;
    }

    protected Host generateHost(InetAddress ip,String HostName)
    {
        final Host host = new Host();
        host.id = currentId++;
        host.ip = ip;
        host.hostName = HostName;
        host.dataRetentionTime = config.defaultDataRetentionTime;
        return host;
    }
}
