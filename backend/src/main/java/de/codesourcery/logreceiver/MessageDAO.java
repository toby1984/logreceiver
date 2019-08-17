package de.codesourcery.logreceiver;

import javax.sql.DataSource;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO
{
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final DataSource datasource;
    private final JDBCHelper helper;

    public MessageDAO(DataSource datasource)
    {
        this.datasource = datasource;
        helper = new JDBCHelper(datasource);
    }

    public List<SyslogMessage> getMessages(Host host, long entryId, boolean ascending, int maxCount) {
        return getMessages(host,"entry_id",Long.toString(entryId),ascending,maxCount);
    }

    public List<SyslogMessage> getMessages(Host host, ZonedDateTime referenceDate, boolean ascending, int maxCount) {
        return getMessages(host,"log_ts",JDBCHelper.toPostgreSQLDate(referenceDate),ascending,maxCount);
    }

    private List<SyslogMessage> getMessages(Host host, String col, String value, boolean ascending, int maxCount)
    {
        final String op;
        final String orderBy;
        if ( ascending ) {
            orderBy = "ASC";
            op = ">";
        } else {
            orderBy = "DESC";
            op = "<";
        }
        final String sql = "SELECT * FROM "+PartitionNamePattern.TABLE_NAME_PREFIX+host.hostName +
                " WHERE "+col+" "+op+" "
            +value+" ORDER BY "+col+" "+orderBy+" LIMIT "+maxCount;

        return helper.execQuery(sql,rs ->
        {
            final List<SyslogMessage> result = new ArrayList<>();
            while ( rs.next() ) {
                final SyslogMessage msg = new SyslogMessage();
                msg.id = rs.getLong("entry_id");
                msg.setTimestamp( rs.getTimestamp("log_ts").toInstant().atZone(UTC) );
                msg.message = rs.getString("msg");
                msg.priority = rs.getShort("priority");
                msg.host = host;
                // TODO: Map more fields
                result.add( msg );
            }
            return result;
        });
    }
}
