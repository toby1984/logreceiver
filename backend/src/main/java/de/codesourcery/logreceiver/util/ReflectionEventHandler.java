package de.codesourcery.logreceiver.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReflectionEventHandler implements EventBus.IEventHandler
{
    private static final Logger LOG = LogManager.getLogger( ReflectionEventHandler.class.getName() );

    private final Supplier<Object> targetSupplier;

    public ReflectionEventHandler(Supplier<Object> targetSupplier) {
        this.targetSupplier = targetSupplier;
    }

    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private List<Method> candidates;
    // @GuardedBy( LOCK )
    private final Map<Class<?>,Method> bestMatch = new HashMap<>();

    @Override
    public boolean handleEvent(IEvent event) throws Exception
    {
        Method bestMatch = getBestMatch( event );
        if ( bestMatch != null ) {
            bestMatch.invoke( event );
            return true;
        }
        return false;
    }

    private Method getBestMatch(IEvent event)
    {
        final List<Method> list;
        synchronized( LOCK)
        {
            final Method result = bestMatch.get( event.getClass() );
            if ( result != null )
            {
                return result;
            }
            if ( bestMatch.containsKey( event.getClass() ) )
            {
                return null;
            }
            if ( candidates == null )
            {
                candidates = findAnnotatedMethods( targetSupplier );
            }
            list = new ArrayList<>( candidates );
        }
        list.removeIf( candidate -> getDistanceToEventClass( candidate.getParameterTypes()[0], event.getClass() ) < 0 );
        list.sort( (a,b) -> {
            int d1 = getDistanceToEventClass( a.getParameterTypes()[0], event.getClass() );
            int d2 = getDistanceToEventClass( b.getParameterTypes()[0], event.getClass() );
            return Integer.compare(d1,d2);
        });
        final Method result = list.isEmpty() ? null : list.get( 0 );
        synchronized(LOCK)
        {
            bestMatch.putIfAbsent( event.getClass(), result );
        }
        return result;
    }

    public static List<Method> findAnnotatedMethods(Supplier<Object> targetSupplier)
    {
        final Object bean = targetSupplier.get();
        final Class<?> beanClass = bean.getClass();
        return Stream.of( beanClass.getMethods() )
                .filter( ReflectionEventHandler::isSuitableMethod )
                .collect( Collectors.toList());
    }

    private static int getDistanceToEventClass(Class<?> argumentClass,Class<?> eventClass) {
        return getDistanceToEventClass( argumentClass, eventClass, 0 );
    }

    private static int getDistanceToEventClass(Class<?> argumentClass,Class<?> eventClass,int dist)
    {
        if ( argumentClass == eventClass ) {
            return dist;
        }
        Class<?> best = null;
        int bestDistance = -1;
        for ( Class<?> iFace : argumentClass.getInterfaces() ) {
            int d = getDistanceToEventClass( iFace, eventClass, dist+1 );
            if ( d >= 0 && ( best == null || d < bestDistance ) ) {
                bestDistance = d;
                best = iFace;
            }
        }
        if ( ! argumentClass.isInterface() ) {
            Class<?> clazz = argumentClass.getSuperclass();
            int depth = 1;
            while ( clazz != Object.class )
            {
                int d = getDistanceToEventClass( clazz, eventClass, dist+depth );
                if ( d >= 0 && ( best == null || d < bestDistance ) ) {
                    best = clazz;
                    bestDistance = d;
                }
                if ( d == 0 ) {
                    break;
                }
                depth++;
                clazz = clazz.getSuperclass();
            }
        }
        if ( best == null ) {
            return -1;
        }
        return dist;
    }

    private static boolean isSuitableMethod(Method m)
    {
        final int flags = m.getModifiers();
        if ( m.getAnnotation(Subscribe.class) != null )
        {
            if ( ! Modifier.isStatic( flags ) &&
                    Modifier.isPublic( flags ) &&
                    ! Modifier.isAbstract( flags ) &&
                    m.getParameterCount() == 1 &&
                    IEvent.class.isAssignableFrom( m.getParameterTypes()[0] ) )
            {
                return true;
            }
            final String msg = "Method "+m.toString()+" on "+m.getDeclaringClass().getName()+" cannot be annotated with @Subscribe";
            LOG.error( "isSuitableMethod(): " + msg );
            throw new RuntimeException( msg );
        }
        return false;
    }
}