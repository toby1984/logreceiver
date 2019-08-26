package de.codesourcery.logreceiver.filtering;

import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.logstorage.MessageDAO;
import de.codesourcery.logreceiver.storage.IHostManager;
import de.codesourcery.logreceiver.util.EternalThread;
import de.codesourcery.logreceiver.util.OptionalBarrier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FilterCallbackManager
{
    private static final Logger LOG = LogManager.getLogger( FilterCallbackManager.class );

    private final Map<InetAddress, ConcurrentLinkedQueue<IFilterCallback>> filterCallbacks = new ConcurrentHashMap<>();

    // @GuardedBy( dirtyIPs )
    private final Set<InetAddress> dirtyIPs = new HashSet<>();

    private static final Duration SLEEP_TIME = Duration.ofSeconds( 60 );

    private volatile boolean cancel;

    private final IHostManager hostManager;
    private final MessageDAO dao;

    // contains the latest entry ID that we processed so far
    // @GuardedBy( watermarks )
    private final Map<InetAddress, Long> watermarks = new HashMap<>();

    private final OptionalBarrier FILTERING_ITERATION_DONE = new OptionalBarrier();

    private final EternalThread thread = new EternalThread( "filter-processor", () -> new EternalThread.Interruptable()
    {
        @Override
        public void cancel()
        {
            cancel = true;
        }

        @Override
        public void run(EternalThread.Context context) throws Exception
        {
            while ( !context.isCancelled() )
            {
                final Set<InetAddress> dirty;
                synchronized (dirtyIPs)
                {
                    if ( dirtyIPs.isEmpty() )
                    {
                        dirty = Collections.emptySet();
                    }
                    else
                    {
                        dirty = new HashSet<>( dirtyIPs );
                        dirtyIPs.clear();
                    }
                }
                if ( LOG.isDebugEnabled() && !dirty.isEmpty() )
                {
                    LOG.info( "Invoking filters for " + dirty.size() + " IPs" );
                }
                for (InetAddress dirtyIP : dirty)
                {
                    final ConcurrentLinkedQueue<IFilterCallback> list = filterCallbacks.get( dirtyIP );
                    if ( list != null )
                    {
                        filter( dirtyIP, list );
                    }
                }
                // wake-up any thread waiting inside #subscribe() method.
                FILTERING_ITERATION_DONE.wakeAll();

                boolean doSleep;
                synchronized (dirtyIPs)
                {
                    doSleep = dirtyIPs.isEmpty();
                }
                if ( doSleep )
                {
                    context.sleep( SLEEP_TIME );
                }
            }
        }
    } );

    private void filter(InetAddress address, Queue<IFilterCallback> callback)
    {
        final Host host = hostManager.getHost( address );
        if ( host == null )
        {
            return;
        }

        final Long oldId;
        synchronized (watermarks)
        {
            oldId = watermarks.get( address );
        }
        if ( LOG.isDebugEnabled() )
        {
            LOG.info( "filter(): [ " + host + " ] Invoking " + filterCallbacks.size() + " filters for " + host.ip +
                      " , watermark=" + oldId );
        }
        final Long newId;
        if ( oldId != null )
        {
            newId = dao.visitNewerMessages( host, new ArrayList<>( callback ), oldId, () -> cancel );
        }
        else
        {
            newId = dao.getLatestMessageId( host );
        }
        if ( newId != null )
        {
            updateWatermark( host, oldId, newId );
        }
    }

    private void updateWatermark(Host host, Long oldValue, Long newValue)
    {
        synchronized (watermarks)
        {
            Long currentId = watermarks.get( host.ip );
            if ( Objects.equals( currentId, oldValue ) )
            {
                watermarks.put( host.ip, newValue );
            }
        }
    }

    public FilterCallbackManager(IHostManager hostManager, MessageDAO dao)
    {
        this.hostManager = hostManager;
        this.dao = dao;
    }

    public void markDirty(InetAddress hostIP)
    {
        synchronized (dirtyIPs)
        {
            dirtyIPs.add( hostIP );
        }
        thread.wakeUp();
    }

    public void register(InetAddress hostIP, IFilterCallback callback)
    {
        if ( hostIP == null ) {
            throw new IllegalArgumentException( "Host IP needs to be set" );
        }
        if ( cancel ) {
            throw new IllegalStateException("Called during shutdown");
        }
        final ConcurrentLinkedQueue<IFilterCallback> list =
        filterCallbacks.computeIfAbsent( hostIP, key -> new ConcurrentLinkedQueue<>() );
        LOG.info("register(): Registered callback "+callback);

        // make sure all watermarks are up-to-date before
        // registering the new filter (so it doesn't receive the whole database
        // if it happens to be the first filter to be added)
        thread.wakeUp();
        try
        {
            FILTERING_ITERATION_DONE.await();
        }
        catch (InterruptedException e)
        {
            // can't help it
        }
        list.add( callback );
    }

    public void unregister(InetAddress hostIP, IFilterCallback callback)
    {
        if ( hostIP == null ) {
            throw new IllegalArgumentException( "Host IP needs to be set" );
        }
        final ConcurrentLinkedQueue<IFilterCallback> list = filterCallbacks.get( hostIP );
        if ( list != null ) {
            final boolean success = list.removeIf( callback::equals );
            LOG.info("unregister(): removed callback "+callback+" [success: "+success+"]");
        }
    }

    @PreDestroy
    public void destroy() throws InterruptedException
    {
        FILTERING_ITERATION_DONE.destroy();
        cancel = true;
        thread.stopThread();
    }

    @PostConstruct
    public void afterPropertiesSet()
    {
        thread.startThread();
    }
}