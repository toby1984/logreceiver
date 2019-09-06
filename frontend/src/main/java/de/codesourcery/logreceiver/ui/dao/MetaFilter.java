package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.entity.SyslogMessage;
import de.codesourcery.logreceiver.events.ApplicationStartedEvent;
import de.codesourcery.logreceiver.events.HostAddedEvent;
import de.codesourcery.logreceiver.events.HostDeletedEvent;
import de.codesourcery.logreceiver.filtering.FilterCallbackManager;
import de.codesourcery.logreceiver.filtering.IFilterCallback;
import de.codesourcery.logreceiver.formatting.PatternLogFormatter;
import de.codesourcery.logreceiver.formatting.PatternLogFormatterCache;
import de.codesourcery.logreceiver.storage.IHostManager;
import de.codesourcery.logreceiver.util.Subscribe;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class MetaFilter implements IFilterCallback
{
    private static final Logger LOG = LogManager.getLogger( MetaFilter.class );

    private final PatternLogFormatterCache formatterCache =
            new PatternLogFormatterCache( 100 );

    @Resource
    private FilterCallbackManager filterManager;

    @Resource
    private IHostManager hostManager;

    // @GuardedBy( callbacksByHost );
    // key is Host, value is Map<Log pattern,List<SubscriptionCallback>>
    private final Map<Host, Map<String,List<SubscriptionCallback>>> callbacksByHost = new HashMap<>();

    public interface IBatchCallback
    {
        /**
         * Invoked to process a batch of matched SyslogMessages for a given
         * subscription.
         *
         * @param sub The subscription that was matched.
         * @param matchCount the number of matched IDs inside the <code>messageIds</code> array, may be zero if this is the final call
         * @param messageIds matched IDs, the actual size of the array may be bigger than the number of matched messages
         *                   so use <code>matchCount</code> instead
         * @param hostIds ID of the host each message came from (indices correspond with entries in the <code>messageIds</code> array)
         * @param finalCall whether the current invocation is - for the time being - the last call since
         *                  the calling {@link SubscriptionCallback} will be discarded after this method returns
         */
        void messagesMatched(Subscription sub,int matchCount,long[] messageIds, long[] hostIds, boolean finalCall);
    }

    @Subscribe
    public void handleEvent(ApplicationStartedEvent event)
    {
        hostManager.getAllHosts().stream()
                .map( HostAddedEvent::new )
                .forEach( MetaFilter.this::handleEvent );
    }

    private static final class SubscriptionCallback
    {
        public final Subscription sub;
        public final Pattern regex;
        public final IBatchCallback batchCallback;

        private int matchedMsgCount = 0;
        private long[] matchedMessageIds = new long[10];
        private long[] matchedHostIds = new long[10];

        SubscriptionCallback(Subscription sub, IBatchCallback batchCallback)
        {
            Validate.notNull( sub, "sub must not be null" );
            Validate.notNull( batchCallback, "batchCallback must not be null" );
            this.sub = sub;
            this.regex = Pattern.compile( sub.expression );
            this.batchCallback = batchCallback;
        }

        public void visit(SyslogMessage message,String formattedMessage, ZonedDateTime now)
        {
            if ( regex.matcher( formattedMessage ).matches() )
            {
                if ( matchedMsgCount == matchedMessageIds.length )
                {
                    final int newLen = 1 + matchedMessageIds.length*2;
                    long[] tmp = new long[ newLen ];
                    System.arraycopy( matchedMessageIds,0,tmp,0,matchedMessageIds.length );
                    matchedMessageIds = tmp;
                    tmp = new long[ newLen ];
                    System.arraycopy( matchedHostIds,0,tmp,0,matchedHostIds.length );
                    matchedHostIds = tmp;

                }
                matchedMessageIds[ matchedMsgCount ] = message.id;
                matchedHostIds[ matchedMsgCount++ ] = message.host.id;
                if ( matchedMsgCount > 1000 )
                {
                    flush(false);
                    matchedMsgCount = 0;
                }
            }
        }

        private void flush(boolean finalCall)
        {
            batchCallback.messagesMatched( sub, matchedMsgCount, matchedMessageIds, matchedHostIds, finalCall );
            matchedMsgCount = 0;
        }

        public void onRemove() {
            flush(true);
        }
    }

    @Override
    public void visit(List<SyslogMessage> messages)
    {
        // key is LogFormatter pattern, value is current message formatted by this pattern
        synchronized (callbacksByHost)
        {
            final ZonedDateTime now = ZonedDateTime.now();
            for (SyslogMessage msg : messages)
            {
                final Map<String, List<SubscriptionCallback>> map = callbacksByHost.get( msg.host );
                if ( map != null )
                {
                    for (String logPattern : map.keySet())
                    {
                        final PatternLogFormatter formatter = formatterCache.get( logPattern );
                        final String formatted = formatter.format( msg );
                        for (SubscriptionCallback cb : map.get( logPattern ) )
                        {
                            cb.visit( msg, formatted , now );
                        }
                    }
                }
            }
        }
    }

    public void register(Subscription sub, IBatchCallback batchCallback)
    {
        Validate.notNull( sub, "sub must not be null" );
        if ( sub.id == 0 ) {
            throw new IllegalArgumentException( "Subscription must be persistent" );
        }
        synchronized( callbacksByHost )
        {
            unregister( sub );
            if ( sub.enabled )
            {
                register( new SubscriptionCallback( sub, batchCallback ) );
            }
        }
    }

    private void register(SubscriptionCallback cb)
    {
        synchronized( callbacksByHost )
        {
            for ( Host h : cb.sub.hostGroup.hosts ) {
                Map<String, List<SubscriptionCallback>> map = callbacksByHost.get( h );
                if ( map == null ) {
                    map = new HashMap<>();
                    callbacksByHost.put(h, map );
                }
                List<SubscriptionCallback> list = map.get( cb.sub.logPattern );
                if ( list == null ) {
                    list = new ArrayList<>();
                    map.put( cb.sub.logPattern, list );
                }
                list.add(cb);
            }
        }
    }

    public Optional<SubscriptionCallback> unregister(Subscription cb)
    {
        final Optional<SubscriptionCallback> callback;
        synchronized( callbacksByHost )
        {
            callback = callbacksByHost.values().stream()
                    .map( Map::values )
                    .flatMap( Collection::stream )
                    .flatMap( Collection::stream )
                    .filter( x -> x.sub.id == cb.id ).findFirst();
        }
        callback.ifPresent( this::unregister );
        return callback;
    }

    private void unregister(SubscriptionCallback cb)
    {
        boolean removed = false;
        synchronized( callbacksByHost )
        {
            final Collection<Map<String, List<SubscriptionCallback>>> maps = callbacksByHost.values();
            for (Iterator<Map<String, List<SubscriptionCallback>>> iterator = maps.iterator(); iterator.hasNext(); )
            {
                final Map<String, List<SubscriptionCallback>> x = iterator.next();
                for (Iterator<List<SubscriptionCallback>> iter = x.values().iterator(); iter.hasNext(); )
                {
                    final List<SubscriptionCallback> list = iter.next();
                    if ( list.remove( cb ) )
                    {
                        removed = true;
                        if ( list.isEmpty() )
                        {
                            iter.remove();
                        }
                    }
                }
                if ( x.isEmpty() ) {
                    iterator.remove();
                }
            }
        }
        if ( removed )
        {
            cb.onRemove();
        }
    }

    @PreDestroy
    public void destroy()
    {
        final Map<Long,SubscriptionCallback> callbacksBySubscriptionId = new HashMap<>();
        synchronized( callbacksByHost )
        {
            for ( Map<String, List<SubscriptionCallback>> x : callbacksByHost.values() ) {
                x.values().forEach( list -> list.forEach( cb -> callbacksBySubscriptionId.put( cb.sub.id, cb ) ) );
            }
            callbacksByHost.clear();
        }
        for ( SubscriptionCallback cb : callbacksBySubscriptionId.values() )
        {
            try
            {
                cb.onRemove();
            }
            catch( Exception e ) {
                LOG.error("destroy(): Unexpected exception while destroying callback for "+cb.sub,e);
            }
        }
    }

    @Override
    public void visit(SyslogMessage message)
    {
        throw new RuntimeException("Should never be called as we already implement visit(List)");
    }

    @Subscribe
    public void handleEvent(HostAddedEvent event) {
        LOG.info("handleEvent(): Registering meta-filter for "+event.newHost);
        filterManager.register( event.newHost.ip, this );
    }

    @Subscribe
    public void handleEvent(HostDeletedEvent event) {
        LOG.info("handleEvent(): Un-registering meta-filter for "+event.deletedHost);
        filterManager.unregister( event.deletedHost.ip, this );
    }
}