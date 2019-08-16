package de.codesourcery.logreceiver;

import java.io.Serializable;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class SyslogMessage implements Serializable
{
    public static final ZoneId UTC = ZoneId.of( "UTC" );

    public short priority;

    private ZonedDateTime cachedTimestamp;

    public int year;
    public int month;
    public int dayOfMonth;
    public int hour;
    public int minute;
    public int second;
    public int secondFrag;
    public boolean posTZ;
    public int tzHours;
    public int tzMinutes;

    public String hostName;
    public InetAddress address;

    public String appName;
    public String procId;
    public String msgId;

    public int paramCount;
    public SDParam[] params = new SDParam[10];

    public String message;

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
                final int factor = posTZ ? 1 : -1;
                final ZoneOffset offset = ZoneOffset.ofHoursMinutes(tzHours * factor, tzMinutes * factor);
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
        posTZ=true;
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
