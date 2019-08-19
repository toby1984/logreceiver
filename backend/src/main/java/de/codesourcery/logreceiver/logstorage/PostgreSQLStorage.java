package de.codesourcery.logreceiver.logstorage;

import de.codesourcery.logreceiver.entity.Configuration;
import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.filtering.FilterCallbackHelper;
import de.codesourcery.logreceiver.util.Interval;
import de.codesourcery.logreceiver.util.EternalThread;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class PostgreSQLStorage implements ISQLLogStorage
{
    private final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( PostgreSQLStorage.class );
    private static final DateTimeFormatter PG_DATE_FORMAT = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ssZ" );

    // Columns for COPY statement
    private static final String COPY_COLUMNS = "priority,log_ts,log_ts_fraction,host_id,app_name,proc_id,msg_id,params,msg";

    private final FilterCallbackHelper callbackHelper;

    private final EternalThread watchdog;

    // @GuardedBy(buffer)
    private final StringBuilder copyBuffer = new StringBuilder();

    private long lastFlushTimestamp = System.currentTimeMillis();

    public final Host host;
    public final DataSource ds;
    public final String partitionName;
    public final String parentTable;
    public final Interval interval;
    public final Configuration config;

    public PostgreSQLStorage(Host host,
                             DataSource datasource,
                             String partition,
                             Interval interval,
                             Configuration config, FilterCallbackHelper callbackHelper) throws SQLException
    {
        this.host = host;
        this.callbackHelper = callbackHelper;
        final String hostName = host.getSQLCompatibleHostName();
        LOG.info("PostgreSQLStorage(): Created storage for "+host+", partition '"+partition+"' and interval "+interval);
        this.ds = datasource;
        this.partitionName = new PartitionNamePattern( hostName, interval.start ).getTableName( config );
        this.interval = interval;
        this.parentTable = createParentTableName( host );
        this.config = config;
        this.watchdog =new EternalThread("psql-storage-"+hostName+"_"+interval, () -> this::flush );
        createTables();
        watchdog.startThread();
    }

    public static String createParentTableName(Host host)
    {
        return PartitionNamePattern.TABLE_NAME_PREFIX+host.getSQLCompatibleHostName();
    }

    private void createTables() throws SQLException
    {
        try ( Connection con = ds.getConnection() )
        {
            con.setAutoCommit( false );
            try ( final Statement stmt = con.createStatement() )
            {
                final String parentTable = createParentTableName(host);

                final String seqName = "seq_"+parentTable;
                stmt.execute( "CREATE SEQUENCE IF NOT EXISTS " + seqName );

                stmt.execute( "CREATE TABLE IF NOT EXISTS " + parentTable + " (" +
                        // TODO: PostgreSQL 11 does not support UNIQUE constraints on columns
                        // that are not part of the partition key (see https://www.postgresql.org/message-id/979372cf-ac21-6b5e-7987-5033fe53c2c2%40lab.ntt.co.jp)
                        // ...maybe this gets fixed and then we can actually declare
                        // this column as unique
                        "entry_id bigint NOT NULL DEFAULT nextval('"+seqName+"')," +
                        "priority smallint NOT NULL," +
                        "log_ts timestamptz NOT NULL," +
                        "log_ts_fraction integer NOT NULL," +
                        "host_id bigint NOT NULL," +
                        "app_name text DEFAULT NULL," +
                        "proc_id text DEFAULT NULL," +
                        "msg_id text DEFAULT NULL," +
                        "params jsonb DEFAULT NULL," +
                        "msg text DEFAULT NULL" +
                        ") PARTITION BY RANGE(log_ts)" );

                stmt.execute( "CREATE INDEX ON "+parentTable+"(log_ts)");

                // TODO: again, this is actually a UNIQUE index but PG11 does not support unique indices for partitioned tables
                // when the column is not part of the partitioning criteria
                stmt.execute( "CREATE INDEX ON "+parentTable+"(entry_id)");

                stmt.execute("CREATE TABLE IF NOT EXISTS "+ partitionName +" PARTITION OF "+parentTable+" FOR VALUES FROM " +
                             "('"+PG_DATE_FORMAT.format( interval.start )+"') TO "+
                             "('"+PG_DATE_FORMAT.format( interval.end)+"')");
            } finally {
                con.setAutoCommit( true );
            }
        }
    }

    private void flushBuffer(String reason) throws SQLException, IOException
    {
        synchronized(copyBuffer)
        {
            if ( copyBuffer.length() == 0 )
            {
                return;
            }
            if ( LOG.isDebugEnabled() ) {
                LOG.debug("flushBuffer(): Flushing buffer ("+ copyBuffer.length()+" characters) , reason: "+reason);
            }

            final String sql = copyBuffer.toString();
            final Reader reader = new InputStreamReader( new ByteArrayInputStream( sql.getBytes() ) );
            try ( final Connection connection = ds.getConnection() )
            {
                final BaseConnection con = connection.unwrap( BaseConnection.class );
                long rowsInserted = new CopyManager( con ).copyIn( "COPY " + partitionName + "("+COPY_COLUMNS+") FROM STDIN (DELIMITER '|')", reader );
                if ( LOG.isTraceEnabled() ) {
                    LOG.trace("flushBuffer(): Wrote "+rowsInserted+" rows to database.");
                }
                copyBuffer.setLength( 0 );
                lastFlushTimestamp = System.currentTimeMillis();
            }
            catch(SQLException e)
            {
                LOG.error("flushBuffer():\n"+sql);
                throw e;
            }
        }
        callbackHelper.markDirty( host.ip );
    }

    @Override
    public void store(Host host, ZonedDateTime timestamp, String sql)
    {
        if ( ! interval.contains( timestamp ) ) {
            LOG.error("store(): Timestamp "+timestamp+" is not in range of "+interval);
            throw new IllegalArgumentException( "Timestamp not in range" );
        }
        if ( host.id != this.host.id ) {
            throw new IllegalArgumentException( "Wrong host ID" );
        }

        final int charsInBuffer;
        synchronized(copyBuffer)
        {
            if ( watchdog.isShutdown() ) {
                LOG.fatal("store(): Shutting down, lost message for "+ host +" @ "+timestamp);
                return;
            }
            if ( copyBuffer.length() > 0 )
            {
                copyBuffer.append( SQLLogWriter.ROW_DELIMITER );
            }
            copyBuffer.append( sql );
            charsInBuffer = copyBuffer.length();
        }

        if ( charsInBuffer > config.maxCharsInBuffer )
        {
            if ( LOG.isDebugEnabled() ) {
                LOG.debug("store(): Flushing (batch size "+config.maxCharsInBuffer+" exceeded)");
            }
            watchdog.wakeUp();
        }
    }

    private void flush(EternalThread.Context ctx) throws SQLException, IOException
    {
        while ( ! ctx.isCancelled() )
        {
            String reason = null;
            boolean doFlush = false;

            if ( ! ctx.sleep( config.flushInterval ) ) {
                break;
            }
            long elapsedMillis = System.currentTimeMillis() - lastFlushTimestamp;
            if ( elapsedMillis >= config.flushInterval.toMillis() )
            {
                reason = "timeout";
                doFlush = true;
            }
            else
            {
                final int charsInBuffer;
                synchronized(copyBuffer) {
                    charsInBuffer = copyBuffer.length();
                }
                if ( charsInBuffer > config.maxCharsInBuffer )
                {
                    reason = "batchsize exceeded";
                    doFlush = true;
                }
            }
            if ( doFlush )
            {
                flushBuffer(reason);
            }
        }
        // make sure to flush on shutdown
        flushBuffer("shutdown");
    }

    public void shutdown() throws InterruptedException
    {
        LOG.info("shutdown(): Shutting down ");
        synchronized( copyBuffer )
        {
            watchdog.stopThread();
        }
    }

    @Override
    public String toString()
    {
        return "PostgreSQLStorage[ "+ host+" , "+interval+" ]";
    }
}