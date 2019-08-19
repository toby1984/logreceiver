package de.codesourcery.logreceiver.parsing;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class JDBCHelper
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SZ");

    public static final int BATCH_SIZE = 500;

    private final DataSource dataSource;

    public interface ResultSetConsumer<T>
    {
        T consume(ResultSet rs) throws SQLException;
    }

    public JDBCHelper(DataSource ds)
    {
        this.dataSource = ds;
    }

    public static String toPostgreSQLDate(ZonedDateTime date) {
        return "'"+FORMATTER.format(date)+"'";
    }

    public int executeUpdate(String sql,Object... arguments)
    {
        try ( Connection con = dataSource.getConnection() )
        {
            con.setAutoCommit( true );
            try ( PreparedStatement stmt = con.prepareStatement( sql ) )
            {
                if ( arguments != null )
                {
                    for (int i = 0; i < arguments.length; i++)
                    {
                        stmt.setObject( i+1, arguments[i] );
                    }
                }
                return stmt.executeUpdate();
            }
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
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
        try ( Connection con = dataSource.getConnection() )
        {
            con.setAutoCommit( false ); // PostgreSQL only supports streaming on transactional queries
            boolean normalExit = false;
            try ( PreparedStatement stmt = con.prepareStatement( sql ) )
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
                    final T result = c.consume(rs);
                    normalExit = true;
                    return result;
                }
            }
            finally
            {
                try
                {
                    con.setAutoCommit(true);
                } catch(SQLException e) {
                   if ( normalExit ) {
                       //noinspection ThrowFromFinallyBlock
                       throw e;
                   }
                }
            }
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T execQuery(String sql, ResultSetConsumer<T> c, Object... arguments)
    {
        try ( Connection con = dataSource.getConnection() )
        {
            con.setAutoCommit( true );
            try ( PreparedStatement stmt = con.prepareStatement( sql ) )
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
                    return c.consume( rs );
                }
            }
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
