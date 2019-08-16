package de.codesourcery.logreceiver;

import javax.sql.DataSource;

public class Main
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( Main.class.getName() );

    public static DataSource ds;
    public static IHostManager hostIdManager;
    public static ILogStorage storage;
    public static SQLLogWriter writer;
    public static UDPServer server;

    public static void main(String[] args) throws Exception
    {
        setup(new Configuration());
    }

    public static void setup(Configuration config) throws Exception
    {
        ds = new DataSourceFactory( config ).getObject();
        hostIdManager = new PostgreSQLHostIdManager( ds, config );
        storage = new DelegatingLogStorage( ds , hostIdManager, config );
        writer = new SQLLogWriter( storage,hostIdManager );
        server = new UDPServer( config, new LogParserFactory( writer ) );
        server.process();
    }

}
