package de.codesourcery.logreceiver.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class EventBusPostProcessor implements BeanPostProcessor
{
    private static final Logger LOG = LogManager.getLogger( EventBusPostProcessor.class );

    private final EventBus eventBus;

    public EventBusPostProcessor(EventBus eventBus)
    {
        this.eventBus = eventBus;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException
    {
        final List<Method> candidates = ReflectionEventHandler.findAnnotatedMethods( () -> bean );
        if ( ! candidates.isEmpty() ) {
            LOG.info("postProcessAfterInitialization(): Registering event handler for bean '"+beanName+"' [ "+bean.getClass().getName()+" ]" );
            eventBus.addEventHandler( new ReflectionEventHandler( () -> bean ) );
        }
        return bean;
    }
}