package de.codesourcery.logreceiver.logstorage;

import de.codesourcery.logreceiver.entity.Configuration;
import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.filtering.FilterCallbackManager;
import de.codesourcery.logreceiver.util.DateUtils;
import de.codesourcery.logreceiver.util.Interval;
import de.codesourcery.logreceiver.parsing.JDBCHelper;
import de.codesourcery.logreceiver.storage.IHostManager;
import de.codesourcery.logreceiver.util.EternalThread;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DelegatingLogStorage implements ISQLLogStorage
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( DelegatingLogStorage.class.getName() );

    // !!! Make sure to adjust parsePartitionName() if you change this pattern
    private static final DateTimeFormatter PARTITION_NAME_TZ_FORMAT = DateTimeFormatter.ofPattern( "YYYYMMDD" );

    private final Map<Long, Map<String,PostgreSQLStorage>> storageByHostAndTime = new ConcurrentHashMap<>();

    private final Thread shutdownHook;
    private volatile boolean shutdown;

    private final DataSource dataSource;
    private final IHostManager hostManager;
    private final Configuration config;
    private final FilterCallbackManager callbackHelper;

    private volatile long lastBackendPurge=0;
    private final AtomicBoolean purgeBackends = new AtomicBoolean();

    private EternalThread watchdog = new EternalThread("partition-pruner", () -> context ->
    {
        cleanUp(context);
        context.sleep( Duration.ofHours(1) );
    });

    private void cleanUp(EternalThread.Context context)
    {
        LOG.info("cleanUp(): Started");
        for ( Host h : hostManager.getAllHosts() )
        {
            if ( context.isCancelled() )
            {
                LOG.info("cleanUp(): Cancelled, terminating.");
                break;
            }
            try
            {
                cleanUp(h);
            }
            catch (SQLException e)
            {
                LOG.error("cleanUp(): Failed for host "+h,e);
            }
        }
    }

    private void cleanUp(Host host) throws SQLException
    {
        if ( host.dataRetentionTime == null ) {
            LOG.debug("cleanUp(): Host "+host+" has cleanup disabled.");
            return;
        }

        LOG.debug("cleanUp(): Invoked for host "+host+", retention time: "+host.dataRetentionTime);

        final JDBCHelper helper = new JDBCHelper( new JdbcTemplate(this.dataSource) );
        final ZonedDateTime now = ZonedDateTime.now();
        final ZonedDateTime earliestDate = now.minus( host.dataRetentionTime );
        for ( String partTableName : findPartitions( host, helper ) )
        {
            final PartitionNamePattern pattern = PartitionNamePattern.parse( partTableName , config );
            final Interval iv = pattern.getInterval( config );
            if ( iv.end.isBefore( earliestDate ) )
            {
                LOG.debug("cleanUp(): Dropping table '"+partTableName+"' for host "+host);
                helper.executeUpdate( "DROP TABLE IF EXISTS "+partTableName );
            } else if ( LOG.isDebugEnabled() ) {
                LOG.debug("cleanUp(): Data in partition '"+partTableName+"' is not older than "+host.dataRetentionTime);
            }
        }
    }

    private List<String> findPartitions(Host host, JDBCHelper helper) throws SQLException
    {
        // 1. find OID of base table
        final String parentTable = PostgreSQLStorage.createParentTableName( host );
        final String sql = "SELECT oid FROM pg_catalog.pg_class WHERE relkind in ('r','p') AND relname=?";

        Long oid = helper.execQuery( sql, rs ->
        {
            Long result = null;
            if ( rs.next() ) {
                result = rs.getLong( 1 );
                if ( rs.wasNull() ) {
                    result = null;
                }
                if ( rs.next() ) {
                    throw new RuntimeException("Internal error, found multiple OIDs for table '"+parentTable+"'");
                }
            }
            return result;
        } , parentTable );
        if ( oid == null )
        {
            LOG.error("findPartitions(): Failed to find parent table '"+parentTable+"' for host "+host);
            return Collections.emptyList();
        }

        // 2. find partitions of base table
        String query = "SELECT c.relname " +
        " FROM " +
        "       pg_catalog.pg_class c, " +
        "       pg_catalog.pg_inherits i" +
        " WHERE c.oid=i.inhrelid AND i.inhparent = ? AND c.relkind='r'";

        return helper.execQuery(query,rs ->
        {
            final List<String> result = new ArrayList<>();
            while ( rs.next() ) {
                String value = rs.getString(1);
                if ( value == null || value.isBlank() ) {
                    throw new RuntimeException("Internal error,NULL/blank partition name?");
                }
                result.add( value );
            }
            return  result;
        }, oid );
    }

    public DelegatingLogStorage(DataSource dataSource, IHostManager hostManager, Configuration config, FilterCallbackManager callbackHelper)
    {
        this.dataSource = dataSource;
        this.hostManager = hostManager;
        this.config = config;
        this.shutdownHook = registerShutdownHook();
        this.callbackHelper = callbackHelper;
        watchdog.startThread();
    }

    @Override
    public void store(Host host, ZonedDateTime timestamp, String sql)
    {
        final Long hostId = host.id;
        Map<String, PostgreSQLStorage> map = storageByHostAndTime.get( hostId );
        if ( map == null )
        {
            // never stored data for this host up to now
            synchronized(storageByHostAndTime)
            {
                map = storageByHostAndTime.get( hostId );
                if ( map == null ) {
                    if ( LOG.isDebugEnabled() )
                    {
                        LOG.debug( "store(): No entry for host ID " + hostId );
                    }
                    map = new ConcurrentHashMap<>();
                    storageByHostAndTime.put(hostId,map);
                }
            }
        }
        final String hostName = host.getSQLCompatibleHostName();
        final String partition = hostName+"_"+toPartitionPart( timestamp );
        PostgreSQLStorage storage = map.get( partition );
        if ( storage == null )
        {
            synchronized(storageByHostAndTime)
            {
                storage = map.get( partition );
                if ( storage == null )
                {
                    if ( LOG.isDebugEnabled() )
                    {
                        LOG.debug( "store(): No entry for partition " + partition);
                    }
                    final Interval interval = toPartitionInterval( timestamp );
                    if ( LOG.isTraceEnabled() ) {
                        LOG.trace("store(): timestamp "+timestamp+" -> interval "+interval);
                    }
                    try
                    {
                        storage = new PostgreSQLStorage( host, dataSource, partition, interval, config, callbackHelper );
                    }
                    catch (SQLException e)
                    {
                        LOG.fatal("store(): Message lost for host #"+hostId+", timestamp "+timestamp,e);
                        return;
                    }
                    map.put( partition, storage );
                }
            }
            purgeStaleBackends();
        }

        if ( shutdown ) {
            LOG.fatal("store(): Shutting down - message lost for host #"+hostId+", timestamp "+timestamp);
            shutdown( storage );
        } else {
            storage.store( host, timestamp, sql );
        }
    }

    private void purgeStaleBackends()
    {
        long elapsedMillis = System.currentTimeMillis() - lastBackendPurge;
        if ( elapsedMillis < config.staleBackendUnloadCheckInterval.toMillis() ) {
            if ( LOG.isDebugEnabled() )
            {
                LOG.debug( "purgeStaleBackends(): Not enough time elapsed since last purge" );
            }
            return;
        }
        if ( !purgeBackends.compareAndSet( false, true ) )
        {
            if ( LOG.isTraceEnabled() )
            {
                LOG.trace( "purgeStaleBackends(): Purge already in progress.");
            }
            return;
        }

        try
        {
            LOG.debug( "purgeStaleBackends(): Looking for stale backends" );
            final ZonedDateTime now = ZonedDateTime.now();
            final List<PostgreSQLStorage> toPurge = new ArrayList<>();
            doWithBackendsIt( it -> {

                while ( it.hasNext() )
                {
                    final PostgreSQLStorage backend = it.next();
                    if ( !backend.interval.contains( now ) )
                    {
                        toPurge.add( backend );
                        it.remove();
                    }
                }
            });
            for (PostgreSQLStorage backend : toPurge)
            {
                try
                {
                    LOG.debug( "purgeStaleBackends(): Unloading stale backend " + backend );
                    backend.shutdown();
                } catch (InterruptedException e)
                {
                    LOG.error( "purgeStaleBackends(): Caught ", e );
                }
            }
        } finally {
            purgeBackends.compareAndSet( true,false );
        }
    }

    private void shutdown(PostgreSQLStorage storage)
    {
        try
        {
            storage.shutdown();
        }
        catch (InterruptedException e)
        {
            LOG.warn("shutdown(): Caught exception while shutting down",e);
        }
    }

    private Interval toPartitionInterval(ZonedDateTime ts)
    {
        final int hoursPerPartition = config.hoursPerPartition;
        final ZonedDateTime utc = ts.withZoneSameInstant( DateUtils.UTC );
        final int startHour = (utc.getHour() / hoursPerPartition )* hoursPerPartition;
        final ZonedDateTime start = utc.withHour( startHour ).withMinute( 0 ).withSecond( 0 ).withNano( 0 );
        final ZonedDateTime end = start.plusHours( hoursPerPartition );
        return new Interval(start,end);
    }

    private String toPartitionPart(ZonedDateTime ts)
    {
        final int hoursPerPartition = config.hoursPerPartition;
        final String pattern = PARTITION_NAME_TZ_FORMAT.format( ts );
        final ZonedDateTime utc = ts.withZoneSameLocal( DateUtils.UTC );
        final int partitionNumber = utc.getHour() / hoursPerPartition;
        return pattern+"_"+partitionNumber;
    }

    @PreDestroy
    public void shutdown() throws InterruptedException
    {
        shutdown = true;
        doWithBackends( this::shutdown );
        watchdog.stopThread();
        Runtime.getRuntime().removeShutdownHook( this.shutdownHook );
    }

    private void doWithBackends(Consumer<PostgreSQLStorage> consumer)
    {
        doWithBackendsIt( it -> {

            while ( it.hasNext() )
            {
                consumer.accept( it.next() );
            }
        });
    }

    private void doWithBackendsIt(Consumer<Iterator<PostgreSQLStorage>> consumer)
    {
        synchronized( storageByHostAndTime )
        {
            for ( Map<String, PostgreSQLStorage> entry : storageByHostAndTime.values() )
            {
                final Iterator<PostgreSQLStorage> it = entry.values().iterator();
                consumer.accept( it );
            }
        }
    }

    private Thread registerShutdownHook()
    {
        LOG.info("registerShutdownHook(): Registering shutdown hook");
        final Thread thread = new Thread( () -> {

            LOG.info("run(): VM is shutting down...");
            try
            {
                shutdown();
            }
            catch (InterruptedException e)
            {
                LOG.error("run(): Caught exception during shutdown",e);
            }
        });
        Runtime.getRuntime().addShutdownHook(thread);
        return thread;
    }
}