package de.codesourcery.logreceiver;

import java.io.Serializable;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class SyslogMessage implements Serializable
{
    public static final ZoneId UTC = ZoneId.of( "UTC" );

    // other
    private ZonedDateTime cachedTimestamp;
    public InetAddress address;
    public Host host;

    // protocol fields
    public String appName;
    public String procId;
    public String msgId;
    public String hostName; // host name from RFC5424 message
    public String message;

    public SDParam[] params = new SDParam[10];
    public short priority;
    public short year;
    public int secondFrag;
    public byte paramCount;
    public byte month, dayOfMonth;
    public byte hour, minute, second;
    public byte tzHours, tzMinutes;

    public int getParamCount() {
        return paramCount;
    }

    public ZonedDateTime getTimestamp()
    {
        if ( cachedTimestamp == null )
        {
            // assumption is that timestamp must ALWAYS be
            // set so we don't bother checking values
            // for validity (ZonedDateTime will do it anyway)
            final ZoneId zoneId;
            if (tzHours == 0 && tzMinutes == 0)
            {
                zoneId = UTC;
            }
            else
            {
                final ZoneOffset offset = ZoneOffset.ofHoursMinutes(tzHours, tzMinutes);
                zoneId = ZoneId.from(offset);
            }
            cachedTimestamp = ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, secondFrag, zoneId);
        }
        return cachedTimestamp;
    }

    public void setTimestamp(ZonedDateTime ts) {
        cachedTimestamp = ts;
    }

    public void addParam(SDParam param)
    {
        if ( paramCount == params.length )
        {
            SDParam[] tmp = new SDParam[ 1+params.length*2 ];
            System.arraycopy(params,0,tmp,0,params.length);
            params = tmp;
        }
        params[paramCount++]=param;
    }

    public void reset()
    {
        priority=0;
        cachedTimestamp = null;
        year=0;
        month=0;
        dayOfMonth=0;
        hour=0;
        minute=0;
        second=0;
        secondFrag=0;
        tzHours=0;
        tzMinutes=0;
        hostName = null;
        address = null;
        appName = null;
        procId = null;
        msgId = null;
        paramCount = 0;
        message = null;
    }
}
