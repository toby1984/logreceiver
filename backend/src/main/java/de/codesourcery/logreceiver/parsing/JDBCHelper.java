package de.codesourcery.logreceiver.parsing;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class JDBCHelper
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SZ");

    public static final int BATCH_SIZE = 500;

    private final DataSource dataSource;

    private boolean insideTransaction;
    private Connection connection;

    public interface ResultSetConsumer<T>
    {
        T consume(ResultSet rs) throws SQLException;
    }

    public interface HelperConsumer {
        void execute(JDBCHelper helper) throws SQLException;
    }

    public JDBCHelper(DataSource ds)
    {
        this.dataSource = ds;
    }

    private Connection currentConnection(boolean maybeStartTransaction) throws SQLException
    {
        if ( connection == null )
        {
            connection = dataSource.getConnection();
            if ( maybeStartTransaction )
            {
                connection.setAutoCommit( false );
                insideTransaction = true;
            }
        }
        return connection;
    }

    private void closeCurrentConnection(boolean success)
    {
        if ( insideTransaction ) {
            return;
        }
        try
        {
            try
            {
                connection.setAutoCommit( true );
            }
            finally
            {
                connection.close();
                insideTransaction = false;
            }
        }
        catch(SQLException e)
        {
            if ( success )
            {
                throw new RuntimeException(e);
            }
        }
    }


    public static String toPostgreSQLDate(ZonedDateTime date) {
        return "'"+FORMATTER.format(date)+"'";
    }

    public void doInTransaction(HelperConsumer c) {

        try ( Connection con = currentConnection(true) )
        {
            boolean success = false;
            try {
                c.execute( this );
                success = true;
            }
            finally
            {
                closeCurrentConnection( success );
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public int executeUpdate(String sql,Object... arguments)
    {
        int[] result = {0};
        doInTransaction( helper ->
        {
            try ( PreparedStatement stmt = helper.currentConnection(false).prepareStatement( sql ) )
            {
                if ( arguments != null )
                {
                    for (int i = 0; i < arguments.length; i++)
                    {
                        stmt.setObject( i+1, arguments[i] );
                    }
                }
                result[0] = stmt.executeUpdate();
            }
        });
        return result[0];
    }

    public long executeInsertAutoGenKey(String sql,Object... arguments)
    {
        long[] result = {0};
        doInTransaction( helper ->
        {
            try (PreparedStatement stmt = helper.currentConnection(false).prepareStatement( sql, Statement.RETURN_GENERATED_KEYS ) )
            {
                if ( arguments != null )
                {
                    for (int i = 0; i < arguments.length; i++)
                    {
                        stmt.setObject( i+1, arguments[i] );
                    }
                }
                stmt.executeUpdate();
                try ( ResultSet rs = stmt.getGeneratedKeys() ) {
                    if ( ! rs.next() ) {
                        throw new IllegalStateException( "No auto-generated key returned?" );
                    }
                    result[0] = rs.getLong(1);
                }
            }
        });
        return result[0];
    }

    public interface IStreamingContext<T>
    {
        void stop();
    }

    public interface IStreamingPredicate<T>
    {
        boolean accept(T item,IStreamingContext context);
    }

    public <T> T execStreamingQuery(String sql, ResultSetConsumer<T> c,Object... arguments)
    {
        Object[] result = {null};
        doInTransaction( helper ->
        {
            try ( PreparedStatement stmt = helper.currentConnection(false).prepareStatement( sql ) )
            {
                stmt.setFetchSize(BATCH_SIZE);
                stmt.setFetchDirection(ResultSet.FETCH_FORWARD);

                if ( arguments != null )
                {
                    for (int i = 0; i < arguments.length; i++)
                    {
                        stmt.setObject( i+1, arguments[i] );
                    }
                }
                try (ResultSet rs = stmt.executeQuery() )
                {
                    result[0] = c.consume(rs);
                }
            }

        } );
        return (T) result[0];
    }

    public <T> T execQuery(String sql, ResultSetConsumer<T> c, Object... arguments)
    {
        final Object[] result = {null};

        doInTransaction( helper ->
        {
            try ( PreparedStatement stmt = helper.currentConnection(false).prepareStatement( sql ) )
            {
                if ( arguments != null )
                {
                    for (int i = 0; i < arguments.length; i++)
                    {
                        stmt.setObject( i+1, arguments[i] );
                    }
                }
                try (ResultSet rs = stmt.executeQuery() )
                {
                    result[0] = c.consume( rs );
                }
            }
        });
        return (T) result[0];
    }

    public static <T> Optional<T> uniqueResult(List<T> data)
    {
        if ( data.size() == 1 ) {
            return Optional.of( data.get( 0 ) );
        }
        if ( data.size() == 0 ) {
            return Optional.empty();
        }
        throw new IllegalStateException("Expected either 0 or 1 result but got "+data.size());
    }
}