package de.codesourcery.logreceiver;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

public class PostgreSQLHostIdManager extends InMemoryHostIdManager
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( PostgreSQLHostIdManager.class.getName() );

    private static final String HOSTS_TABLE = "log_hosts";
    private static final String PROJECTION_COLUMNS = "host_id,ip,name,data_retention_hours";

    private final DataSource ds;

    public PostgreSQLHostIdManager(DataSource ds, Configuration config) throws SQLException
    {
        super(config);
        this.ds = ds;
        createTable();
        loadTable();
    }

    @Override
    protected Host generateHost(InetAddress ip, String HostName)
    {
        final Host host = new Host();
        host.ip = ip;
        host.hostName = HostName;
        host.dataRetentionTime = config.defaultDataRetentionTime;

        try ( Connection con = ds.getConnection() )
        {
            final String sql = "INSERT INTO "+HOSTS_TABLE+" (ip,name,data_retention_hours) VALUES (?::inet,?,?)";
            try (PreparedStatement stmt = con.prepareStatement( sql, Statement.RETURN_GENERATED_KEYS ) )
            {
                final String ipAsString = ip.getHostAddress();
                stmt.setObject( 1, ipAsString);
                stmt.setString( 2, HostName );
                stmt.setInt( 3, (int) config.defaultDataRetentionTime.toHours() );
                stmt.executeUpdate();
                try ( ResultSet rs = stmt.getGeneratedKeys() ) {
                    if ( ! rs.next() ) {
                        throw new RuntimeException("Statement returned no key ?");
                    }
                    final long id = ((Number) rs.getObject(1)).longValue();
                    if ( LOG.isTraceEnabled() ) {
                        LOG.trace("generateId(): IP "+ip+", host '"+HostName+"' -> "+id);
                    }
                    host.id = id;
                    return host;
                }
            }
        }
        catch(SQLException e) {
            LOG.error("generateId(): Caught ",e);
            throw new RuntimeException( e );
        }
    }

    private void loadTable() throws SQLException {

        try ( Connection con = ds.getConnection() )
        {
            try (Statement stmt = con.createStatement())
            {
                try ( ResultSet rs = stmt.executeQuery( "SELECT "+PROJECTION_COLUMNS+" FROM "+HOSTS_TABLE ) )
                {
                    int count = 0;
                    synchronized (LOCK)
                    {
                        super.hostsById.clear();
                        super.hostsByIP.clear();
                        while ( rs.next() )
                        {
                            final Host host = new Host();

                            final Long hostId = rs.getLong( "host_id" );
                            host.id = hostId;

                            final int retentionTimeHours = rs.getInt( "data_retention_hours" );
                            if ( ! rs.wasNull() ) {
                                host.dataRetentionTime = Duration.ofHours( retentionTimeHours );
                            }
                            final String ip = rs.getString("ip");
                            try
                            {
                                // TODO: Potential for speeding things up as we know the string is definitely not a hostname
                                host.ip = InetAddress.getByName(ip);
                            }
                            catch (UnknownHostException e)
                            {
                                // should never happen as we're parsing a literal IP
                                throw new RuntimeException(e);
                            }
                            host.hostName = rs.getString( "name" );

                            super.hostsById.put( hostId, host );
                            super.hostsByIP.put( host.ip, host );
                            count++;
                        }
                    }
                    LOG.info("loadTable(): Loaded "+count+" host entries.");
                }
            }
        }
    }

    private void createTable() throws SQLException
    {
        try ( Connection con = ds.getConnection() )
        {
            con.setAutoCommit( false );
            try ( Statement stmt = con.createStatement() )
            {
                stmt.execute( "CREATE SEQUENCE IF NOT EXISTS seq_log_hosts");

                // make sure to adjust PROJECTION_COLUMNS when changing columns here
                stmt.execute( "CREATE TABLE IF NOT EXISTS "+HOSTS_TABLE+" (" +
                              "host_id bigint PRIMARY KEY DEFAULT nextval('seq_log_hosts')," +
                              "ip inet UNIQUE NOT NULL," +
                              "name text DEFAULT NULL," +
                              "data_retention_hours integer NOT NULL)");

            } finally {
                con.setAutoCommit( true );
            }
        }
    }
}
