package de.codesourcery.logreceiver;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Duration;

public class Host implements Serializable
{
    public long id;
    public InetAddress ip;
    public String hostName;
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

    public Host copy() {
        return new Host(this);
    }
}
