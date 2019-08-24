package de.codesourcery.logreceiver.parsing;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class JDBCHelper
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SZ");

    public static final int BATCH_SIZE = 500;

    private final JdbcTemplate template;

    public interface HelperConsumer {
        void execute(JDBCHelper helper) throws SQLException;
    }

    public JDBCHelper(JdbcTemplate template)
    {
        this.template = template;
    }

    public static String toPostgreSQLDate(ZonedDateTime date) {
        return "'"+FORMATTER.format(date)+"'";
    }

    public void doInTransaction(HelperConsumer c) {
        template.execute( (ConnectionCallback<Void>) connection ->
        {
            c.execute( JDBCHelper.this );
            return null;
        });
    }

    public int executeUpdate(String sql,Object... arguments)
    {
        return template.update( sql, arguments );
    }

    private PreparedStatement applyParameter(PreparedStatement stmt,Object... arguments) throws SQLException
    {
        if ( arguments != null )
        {
            for (int i = 0, len = arguments.length; i < len; i++)
            {
                stmt.setObject( (i+1), arguments[i] );
            }
        }
        return stmt;
    }

    public long executeInsertAutoGenKey(String sql,Object... arguments)
    {
        final GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        template.update( connection ->
        {
            final PreparedStatement stmt = connection.prepareStatement( sql, Statement.RETURN_GENERATED_KEYS );
            return applyParameter( stmt, arguments );
        },  keyHolder );
        return keyHolder.getKey().longValue();
    }

    public <T> T execStreamingQuery(String sql, ResultSetExtractor<T> c, Object... arguments)
    {
        return template.execute( (ConnectionCallback<T>) connection ->
        {
            try ( PreparedStatement stmt = connection.prepareStatement( sql ) )
            {
                stmt.setFetchSize(BATCH_SIZE);
                stmt.setFetchDirection(ResultSet.FETCH_FORWARD);
                applyParameter( stmt, arguments );
                try (ResultSet rs = stmt.executeQuery() )
                {
                    return c.extractData(rs);
                }
            }
        });
    }

    public long queryForLong(String sql, Object... arguments )
    {
        try( Connection con = template.getDataSource().getConnection() )
        {
            try ( PreparedStatement stmt = con.prepareStatement( sql ) ) {
                applyParameter( stmt, arguments );
                try( ResultSet rs = stmt.executeQuery() )
                {
                    if ( ! rs.next() ) {
                        throw new EmptyResultDataAccessException( 1 );
                    }
                    return rs.getLong( 1 );
                }
            }
        }
        catch (SQLException e)
        {
            throw template.getExceptionTranslator().translate( "queryForLong",sql,e );
        }
    }

    public <T> T execQuery(String sql, ResultSetExtractor<T> c, Object... arguments)
    {
        return template.query( sql, c,arguments);
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