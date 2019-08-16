package de.codesourcery.logreceiver;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class Interval
{
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SZ");

    public final ZonedDateTime start;
    public final long startEpochSeconds;
    public final ZonedDateTime end;
    public final long endEpochSeconds;

    public Interval(ZonedDateTime start, ZonedDateTime end)
    {
        if ( start.compareTo( end ) >= 0 ) {
            throw new IllegalArgumentException( "start >= end ?" );
        }
        this.start = start;
        this.startEpochSeconds = start.toEpochSecond();
        this.end = end;
        this.endEpochSeconds = end.toEpochSecond();
    }

    @Override
    public String toString()
    {
        return formatter.format( start )+ "- " + formatter.format( end );
    }

    public boolean contains(ZonedDateTime ts)
    {
        final long epochSeconds = ts.toEpochSecond();
        return startEpochSeconds <= epochSeconds && epochSeconds < endEpochSeconds;
    }
}
