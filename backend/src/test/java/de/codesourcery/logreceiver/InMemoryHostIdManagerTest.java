package de.codesourcery.logreceiver;

import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;

public class InMemoryHostIdManagerTest
{
    private InMemoryHostIdManager manager;

    @Before
    public void setup() {
        manager = new InMemoryHostIdManager(new Configuration());
    }

    @Test
    public void testGetHostId() throws UnknownHostException
    {
        final Host id1 = manager.getOrCreateHost(InetAddress.getLocalHost(), "host" );
        final Host id2 = manager.getOrCreateHost( InetAddress.getLocalHost(), "HOST" );
        assertEquals(1L,id1.id);
        assertEquals(id1,id2);
        final String name = id1.getSQLCompatibleHostName();
        assertEquals("host",name);
    }
}
