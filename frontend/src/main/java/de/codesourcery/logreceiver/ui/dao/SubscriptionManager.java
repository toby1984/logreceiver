package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.events.ApplicationStartedEvent;
import de.codesourcery.logreceiver.filtering.FilterCallbackManager;
import de.codesourcery.logreceiver.logstorage.MessageDAO;
import de.codesourcery.logreceiver.ui.util.SpringUtil;
import de.codesourcery.logreceiver.util.Subscribe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ConcurrentModificationException;
import java.util.List;

@Component
public class SubscriptionManager
{
    private static final Logger LOG = LogManager.getLogger( SubscriptionManager.class );

    @Resource
    private IDatabaseBackend backend;

    @Resource
    private MetaFilter metaFilter;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final MetaFilter.IBatchCallback batchCallback = new MetaFilter.IBatchCallback()
    {
        @Override
        public void messagesMatched(Subscription sub, int matchCount, long[] messageIds, long[] hostIds, boolean finalCall)
        {
            backend.storeMatchedMessages( sub,matchCount,messageIds, hostIds );
        }
    };

    @Resource
    private FilterCallbackManager filterManager;

    public SubscriptionManager() {
    }

    public List<Subscription> getAllSubscriptions(boolean includeDisabled) {
        return backend.getAllSubscriptions( includeDisabled );
    }

    public List<Subscription> getSubscriptions(User user) {
        return backend.getSubscriptions( user );
    }

    @Transactional
    public void deleteSubscription(Subscription sub)
    {
        final Subscription copy = sub.copy();
        backend.deleteSubscription( copy );
        SpringUtil.onSuccessfulCommit( () -> metaFilter.unregister( copy ) );
    }

    @Subscribe
    public void handleEvent(ApplicationStartedEvent event)
    {
        backend.getAllSubscriptions( false ).forEach( sub -> metaFilter.register( sub, batchCallback ) );
    }

    @Transactional
    public boolean saveSubscription(Subscription newState)
    {
        if ( newState.id != 0 )
        {
            Subscription old = backend.getSubscription( newState.id ).orElse( null );
            if ( old == null ) {
                LOG.error( "saveSubscription(): Subscription " + newState.id + " has been deleted in the meantime" );
                throw new ConcurrentModificationException( "Subscription " + newState.id + " has been deleted in the meantime" );
            }
        }
        final boolean success = backend.saveSubscription( newState );
        if ( success )
        {
            final Subscription copy = newState.copy();
            SpringUtil.onSuccessfulCommit( () -> metaFilter.register( copy, batchCallback ) );
        }
        return success;
    }
}