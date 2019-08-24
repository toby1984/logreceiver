package de.codesourcery.logreceiver.logstorage;

import de.codesourcery.logreceiver.IAPI;
import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.entity.SyslogMessage;
import de.codesourcery.logreceiver.filtering.IFilterCallback;
import de.codesourcery.logreceiver.parsing.JDBCHelper;
import de.codesourcery.logreceiver.util.DateUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MessageDAO
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( MessageDAO.class );

    private final JDBCHelper helper;

    public Long getLatestMessageId(Host host)
    {
        final String sql = "SELECT max(entry_id) AS entry_id FROM "+PartitionNamePattern.parentTableName( host );
        return helper.queryForLong( sql );
    }

    @FunctionalInterface
    private interface ResultSetExtractor
    {
        List<SyslogMessage> extract(Host host, ResultSet rs) throws SQLException;
    }

    private static class DefaultResultSetExtractor implements ResultSetExtractor
    {
        @Override
        public List<SyslogMessage> extract(Host host, ResultSet rs) throws SQLException
        {
            final List<SyslogMessage> result = new ArrayList<>();
            while ( rs.next() )
            {
                final SyslogMessage msg = parse(host, rs);
                if ( includeInResult( msg ) )
                {
                    result.add(msg);
                    if ( exitLoop(result) ) {
                        break;
                    }
                }
            }
            return result;
        }

        public static SyslogMessage parse(Host host, ResultSet rs) throws SQLException
        {
            final SyslogMessage msg = new SyslogMessage();
            msg.id = rs.getLong("entry_id");
            msg.priority = rs.getShort("priority");
            ZonedDateTime ts = rs.getTimestamp( "log_ts" ).toInstant().atZone( DateUtils.UTC );
            final int fraction = rs.getInt( "log_ts_fraction" );
            if ( ! rs.wasNull() ) {
                ts = ts.withNano( fraction );
            }
            msg.timestamp = ts;
            final long hostId = rs.getLong("host_id");
            if ( hostId != host.id ) {
                // should never happen
                throw new RuntimeException("Host ID returned by database does not match host passed to this function?");
            }
            msg.appName = rs.getString("app_name");
            msg.procId  = rs.getString("proc_id");
            msg.msgId = rs.getString("msg_id");
            String json = rs.getString("params");
            if ( ! rs.wasNull() && ! "null".equals(json) ) {
                // TODO: implement me
                // msg.addParam( ...  );
                throw new RuntimeException("Not implemented - unmarshalling JSON parameter string");
            }
            msg.message = rs.getString("msg");
            msg.host = host;
            return msg;
        }

        protected boolean includeInResult(SyslogMessage message) {
            return true;
        }

        protected boolean exitLoop(List<SyslogMessage> result) {
            return false;
        }
    }

    private static final class FilteringResultSetExtractor extends DefaultResultSetExtractor
    {
        private final int limit;
        private final Predicate<SyslogMessage> predicate;

        private FilteringResultSetExtractor(Predicate<SyslogMessage> predicate, int limit)
        {
            this.limit = limit;
            this.predicate = predicate;
        }

        @Override
        protected boolean includeInResult(SyslogMessage message)
        {
            return predicate.test(message );
        }

        @Override
        protected boolean exitLoop(List<SyslogMessage> result)
        {
            return result.size() >= limit;
        }
    };

    public MessageDAO(JdbcTemplate template)
    {
        helper = new JDBCHelper(template);
    }

    public List<SyslogMessage> getLatestMessages(Host host, IFilterCallback callback, int maxCount)
    {
        // TODO: limit currently cannot be enforced on server-side as we're doing the matching on the client-side...which is sloooow
        final String sql = "SELECT * FROM "+ PartitionNamePattern.parentTableName( host )+" ORDER BY entry_id DESC";

        final FilteringResultSetExtractor extractor = new FilteringResultSetExtractor(callback.getPredicate(),maxCount);
        return helper.execStreamingQuery( sql, rs -> extractor.extract( host,rs ) );
    }

    /**
     * Visit all messages for a given host that have a DB primary key greater than X.
     *
     * Messages are visi+ted oldest to newest.
     *
     * @param host
     * @param filters
     * @param latestId ID or null to visit ALL messages
     * @return DB primary key of latest log entry that has been visited (may be NULL if DB was empty)
     */
    public Long visitNewerMessages(Host host, List<IFilterCallback> filters, Long latestId, BooleanSupplier cancel)
    {
        final String sql;
        if ( latestId == null )
        {
            sql = "SELECT * FROM " + PartitionNamePattern.parentTableName( host ) + " ORDER BY entry_id ASC";
        } else {
            sql = "SELECT * FROM " + PartitionNamePattern.parentTableName( host ) + " WHERE entry_id > "+latestId+" ORDER BY entry_id ASC";
        }
        final org.springframework.jdbc.core.ResultSetExtractor<Long> c = rs ->
        {
            Long lastId = null;
            final List<SyslogMessage> batch = new ArrayList<>();
            final Consumer<IFilterCallback> filterVisitor = x -> x.visit( batch );
            while ( rs.next() )
            {
                final SyslogMessage msg = DefaultResultSetExtractor.parse( host, rs );
                lastId = msg.id;
                batch.add( msg );
                if ( batch.size() >= JDBCHelper.BATCH_SIZE )
                {
                    if ( cancel.getAsBoolean() ) {
                        break;
                    }
                    filters.forEach( filterVisitor );
                    batch.clear();
                }
            }
            if ( ! batch.isEmpty() && ! cancel.getAsBoolean() ) {
                filters.forEach( filterVisitor );
            }
            return lastId;
        };
        return helper.execStreamingQuery( sql, c );
    }

    public List<SyslogMessage> getMessages(Host host, IFilterCallback callback, IAPI.PagingDirection direction,
                                           long refLogEntryId, int maxCount)
    {
        String sortDirection;
        final String condition;
        switch (direction)
        {
            case FORWARD_IN_TIME:
                condition = "entry_id > "+refLogEntryId;
                sortDirection = "ASC";
                break;
            case BACKWARD_IN_TIME:
                condition = "entry_id < "+refLogEntryId;
                sortDirection = "DESC";
                break;
            default:
                throw new RuntimeException( "Unhandled switch/case: " + direction );
        }
        final String sql = "SELECT * FROM "+ PartitionNamePattern.parentTableName( host )+" WHERE "+condition+" ORDER BY entry_id "+sortDirection;

        final FilteringResultSetExtractor extractor = new FilteringResultSetExtractor(callback.getPredicate(),maxCount);
        return helper.execStreamingQuery( sql, rs -> extractor.extract( host,rs ) );
    }
}