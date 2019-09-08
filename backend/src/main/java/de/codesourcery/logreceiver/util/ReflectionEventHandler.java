package de.codesourcery.logreceiver.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
            bestMatch.invoke( targetSupplier.get(), event );
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
        final Set<Method> allMethods = new HashSet<>();
        allMethods.addAll( Arrays.asList( beanClass.getDeclaredMethods() ) );
        allMethods.addAll( Arrays.asList( beanClass.getMethods() ) );

        return allMethods.stream()
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
        if ( ! argumentClass.isAssignableFrom( eventClass ) ) {
            return -1;
        }
        Class<?> best = null;
        int bestDistance = -1;
        for ( Class<?> iFace : eventClass.getInterfaces() ) {
            int d = getDistanceToEventClass( argumentClass, iFace, dist+1 );
            if ( d >= 0 && ( best == null || d < bestDistance ) ) {
                bestDistance = d;
                best = iFace;
            }
        }
        Class<?> clazz = eventClass.getSuperclass();
        int depth = 1;
        while ( clazz != Object.class )
        {
            int d = getDistanceToEventClass( argumentClass, clazz, dist+depth );
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
        if ( best == null ) {
            return -1;
        }
        return bestDistance;
    }

    private static boolean isSuitableMethod(Method m)
    {
        final int flags = m.getModifiers();
        if ( m.getAnnotation(Subscribe.class) != null )
        {
            System.out.println("Checking "+m);
            if ( ! Modifier.isStatic( flags ) &&
                    Modifier.isPublic( flags ) &&
                    ! Modifier.isAbstract( flags ) &&
                    m.getParameterCount() == 1 &&
                    m.getReturnType() == Void.TYPE &&
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