package de.codesourcery.logreceiver.ui.util;

import de.codesourcery.logreceiver.events.ApplicationStartedEvent;
import de.codesourcery.logreceiver.util.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class ContextStartupListener implements ApplicationListener
{
    private static final Logger LOG = LogManager.getLogger( ContextStartupListener.class.getName() );

    @Resource
    private EventBus eventBus;

    @Override
    public void onApplicationEvent(ApplicationEvent event)
    {
        if ( event instanceof ContextStartedEvent )
        {
            LOG.info("onApplicationEvent(): Application started.");
            eventBus.send( new ApplicationStartedEvent() );
        }
    }
}
