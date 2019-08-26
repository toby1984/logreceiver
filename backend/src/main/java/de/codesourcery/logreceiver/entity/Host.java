package de.codesourcery.logreceiver.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Duration;

public class Host implements Serializable
{
    /* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * Make sure to adjust @JsonIgnoreProperties annotation on
     * SyslogMessage#host when adding/removing member fields here
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
    @JsonProperty("id")
    public long id;
    @JsonProperty("ip")
    public InetAddress ip;
    @JsonProperty("name")
    public String hostName;
    @JsonIgnore
    public Duration dataRetentionTime;

    public Host() {
    }

    public Host(Host other)
    {
        this.id = other.id;
        this.ip = other.ip;
        this.hostName = other.hostName;
        this.dataRetentionTime = other.dataRetentionTime;
    }

    @JsonIgnore
    public String getSQLCompatibleHostName()
    {
        if ( hostName != null && ! hostName.isEmpty() ) {
            return hostName.toLowerCase();
        }
        if ( ip instanceof Inet4Address) {
            return ip.getHostAddress().replace('.', '_');
        }
        return ip.getHostAddress().replace(':', '_').replace('.', '_');
    }

    @Override
    public String toString()
    {
        return (ip==null? "<NULL>" : ip.getHostAddress()) +" ("+ hostName +")#"+id;
    }

    public String toPrettyString()
    {
        if ( StringUtils.isNotBlank(hostName) ) {
            return hostName+" ("+ip.getHostAddress()+")";
        }
        return ip.getHostAddress();
    }

    public Host copy() {
        return new Host(this);
    }
}
