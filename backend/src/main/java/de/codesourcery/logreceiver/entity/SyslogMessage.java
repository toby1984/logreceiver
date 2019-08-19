package de.codesourcery.logreceiver.entity;

import org.apache.logging.log4j.core.net.Facility;

import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class SyslogMessage implements Serializable
{
    // other
    public long id;
    public Host host;

    // protocol fields
    public ZonedDateTime timestamp;
    public short priority;
    public String appName;
    public String procId;
    public String msgId;
    public String hostName; // host name from RFC5424 message
    public String message;
    public SDParam[] params = new SDParam[10];

    public byte paramCount;

    public SyslogMessage() {
    }

    public SyslogMessage(SyslogMessage other)
    {
        this.id = other.id;
        this.host = other.host;
        this.timestamp = other.timestamp;
        this.priority = other.priority;
        this.appName = other.appName;
        this.procId = other.procId;
        this.msgId = other.msgId;
        this.hostName = other.hostName;
        this.message = other.message;
        this.params = other.params;
        this.paramCount = other.paramCount;
    }

    /**
     * Returns a SHALLOW copy of this instance.
     * @return
     */
    public SyslogMessage copy() {
        return new SyslogMessage(this );
    }

    public int getParamCount() {
        return paramCount;
    }

    public List<SDParam> getParameters() {
        final List<SDParam> result = new ArrayList<>();
        for ( int i = 0, len = getParamCount() ; i < len ; i++) {
            result.add( params[i] );
        }
        return result;
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
        host = null;
        priority=0;
        timestamp = null;
        hostName = null;
        appName = null;
        procId = null;
        msgId = null;
        paramCount = 0;
        message = null;
    }

    public interface TZVisitor<T> {
        T visit(int tzHours, int tzMinutes, boolean posTZ);
    }

    public <T> T visitTZOffset(TZVisitor<T> visitor)
    {
        final ZoneOffset offset = ZoneOffset.from( timestamp );
        int seconds = offset.getTotalSeconds();
        final int tzHours;
        final int tzMinutes;
        boolean posTZ = true;
        if ( seconds < 0 ) {
            posTZ = false;
            seconds = -seconds;
        }
        tzHours = seconds / (60*60);
        seconds -= tzHours*(60*60);
        tzMinutes = seconds / 60;
        return visitor.visit( tzHours, tzMinutes, posTZ );
    }

    public int getSeverity() {
        return priority & 0b111;
    }

    public int getFacility() {
        return (priority >>> 3);
    }
}
