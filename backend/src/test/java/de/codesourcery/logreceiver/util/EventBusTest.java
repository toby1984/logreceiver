package de.codesourcery.logreceiver.util;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class EventBusTest
{
    private EventBus eventBus;

    @Before
    public void setup() {
        eventBus = new EventBus();
    }

    @Test
    public void send()
    {
        final AtomicReference<IEvent> received =
            new AtomicReference<>();

        eventBus.addEventHandler( new EventBus.IEventHandler()
        {
            @Override
            public boolean handleEvent(IEvent event) throws Exception
            {
                assertTrue( received.compareAndSet( null, event ) );
                return true;
            }
        });
        final IEvent event = new IEvent() {};
        eventBus.send( event );
        assertSame( event, received.get() );
    }
}