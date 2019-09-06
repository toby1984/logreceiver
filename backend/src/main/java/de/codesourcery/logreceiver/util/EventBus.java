package de.codesourcery.logreceiver.util;

import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EventBus
{
    private static final Logger LOG = LogManager.getLogger( EventBus.class );

    public interface IEventHandler
    {
        boolean handleEvent(IEvent event) throws Exception;
    }

    // @GuardedBy( handlers )
    private final List<IEventHandler> handlers =
            new ArrayList<>();

    public void addEventHandler(IEventHandler handler) {
        Validate.notNull( handler, "handler must not be null" );
        synchronized (handlers)
        {
            handlers.add( handler );
        }
    }

    public void removeEventHandler(IEventHandler handler) {
        Validate.notNull( handler, "handler must not be null" );
        synchronized (handlers)
        {
            handlers.remove( handler );
        }
    }
    public void send(IEvent event)
    {
        final List<IEventHandler> copy;
        synchronized (handlers) {
            copy = new ArrayList<>(handlers);
        }
        boolean handled = false;
        for ( IEventHandler h : copy )
        {
            try
            {
                handled |= h.handleEvent( event );
            }
            catch(Exception e) {
                LOG.error("send(): Event handler "+h+" failed",e);
            }
        }
        if ( ! handled ) {
            LOG.warn("send(): Unhandled event: "+event);
        }
    }
}