package de.codesourcery.logreceiver;

import de.codesourcery.logreceiver.entity.Configuration;
import de.codesourcery.logreceiver.filtering.FilterCallbackHelper;
import de.codesourcery.logreceiver.logstorage.DelegatingLogStorage;
import de.codesourcery.logreceiver.logstorage.ISQLLogStorage;
import de.codesourcery.logreceiver.logstorage.MessageDAO;
import de.codesourcery.logreceiver.logstorage.PostgreSQLHostIdManager;
import de.codesourcery.logreceiver.logstorage.SQLLogWriter;
import de.codesourcery.logreceiver.parsing.LogParserFactory;
import de.codesourcery.logreceiver.receiving.UDPServer;
import de.codesourcery.logreceiver.storage.IHostManager;
import de.codesourcery.logreceiver.util.DataSourceFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class Main
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( Main.class.getName() );

    public static DataSource ds;
    public static IHostManager hostIdManager;
    public static ISQLLogStorage storage;
    public static SQLLogWriter writer;
    public static UDPServer server;
    public static MessageDAO messageDAO;

    public static void main(String[] args) throws Exception
    {
        setup(new Configuration());
    }

    public static void setup(Configuration config) throws Exception
    {
        ds = new DataSourceFactory( config ).getObject();
        messageDAO = new MessageDAO( new JdbcTemplate(ds) );
        hostIdManager = new PostgreSQLHostIdManager( ds, config );

        final FilterCallbackHelper callbackHelper = new FilterCallbackHelper( hostIdManager, messageDAO );
        callbackHelper.afterPropertiesSet();

        storage = new DelegatingLogStorage( ds , hostIdManager, config, callbackHelper );
        writer = new SQLLogWriter( storage,hostIdManager );

        server = new UDPServer( config, new LogParserFactory( writer, hostIdManager ) );
        server.process();
    }
}