package de.codesourcery.logreceiver;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartitionNamePattern
{
    public static final String TABLE_NAME_PREFIX = "logs_";
    private static final Pattern NAME_PATTERN = Pattern.compile( Pattern.quote(TABLE_NAME_PREFIX)+"(.*?)_([0-9]{9,})_([0-9]+)");

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern( "yyyyMMddHH" );
    private static final ZoneId UTC = ZoneId.of( "UTC" );

    private final String hostname;
    private final ZonedDateTime startTime;

    public PartitionNamePattern(String hostname,ZonedDateTime startTime)
    {
        if ( ! startTime.getZone().equals( UTC ) ) {
            throw new IllegalArgumentException( "Expected a timestamp in UTC" );
        }
        if ( hostname == null || hostname.isBlank() ) {
            throw new IllegalArgumentException( "Expected a non-blank host name" );
        }
        this.hostname = hostname;
        this.startTime = startTime;
    }

    public String getTableName(Configuration config)
    {
        final int partitionNumber = startTime.getHour() / config.hoursPerPartition;
        return "logs_"+hostname+"_"+DATE_PATTERN.format( startTime )+"_"+partitionNumber;
    }

    public Interval getInterval(Configuration config) {
        return new Interval( startTime, startTime.plusHours( config.hoursPerPartition ) );
    }

    public static PartitionNamePattern parse(String tableName, Configuration config)
    {
        final Matcher m = NAME_PATTERN.matcher( tableName );
        if ( ! m.matches() || m.groupCount() != 3 ) {
            throw new IllegalArgumentException( "Unexpected table name: "+tableName );
        }
        final String hostName = m.group(1);
        final TemporalAccessor accessor = DATE_PATTERN.parse( m.group( 2 ) );
        final int partitionNo = Integer.parseInt( m.group(3) );

        final int year = accessor.get( ChronoField.YEAR );
        final int month = accessor.get( ChronoField.MONTH_OF_YEAR);
        final int day = accessor.get( ChronoField.DAY_OF_MONTH );

        ZonedDateTime dt = ZonedDateTime.of( year,month,day,0,0,0,0,UTC);
        dt = dt.plusHours( partitionNo * config.hoursPerPartition );
        return new PartitionNamePattern(hostName,dt);
    }

    public static String parentTableName(Host host) {
        return PartitionNamePattern.TABLE_NAME_PREFIX + host.hostName;
    }
}
