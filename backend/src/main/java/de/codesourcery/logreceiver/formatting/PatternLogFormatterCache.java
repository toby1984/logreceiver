package de.codesourcery.logreceiver.formatting;

import de.codesourcery.logreceiver.util.EternalThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PatternLogFormatterCache
{
    private static final Logger LOG = LogManager.getLogger( PatternLogFormatterCache.class );

    private static final class CacheEntrySnapshot implements Comparable<CacheEntrySnapshot> {
        public final CacheEntry entry;
        public final int usageCount;

        private CacheEntrySnapshot(CacheEntry entry)
        {
            this.entry = entry;
            this.usageCount = entry.usageCount.get();
        }

        @Override
        public int compareTo(CacheEntrySnapshot o)
        {
            return Integer.compare( this.usageCount, o.usageCount );
        }
    }

    private static final class CacheEntry
    {
        public final String pattern;
        public final PatternLogFormatter formatter;
        public final AtomicInteger usageCount=
                new AtomicInteger(1);

        private CacheEntry(String pattern)
        {
            this.pattern = pattern;
            this.formatter = PatternLogFormatter.ofPattern( pattern );
        }
    }

    private final int maxCacheSize;
    private final ConcurrentHashMap<String,CacheEntry>
     cache = new ConcurrentHashMap<>();

    private final EternalThread cacheCleaner = new EternalThread( "pattern-log-cache-cleaner", () ->
            new EternalThread.Interruptable()
            {
                @Override
                public void run(EternalThread.Context context) throws Exception
                {
                    while ( ! context.isCancelled() )
                    {
                        if ( context.sleep( Duration.ofSeconds(60) ) )
                        {
                            // be very defensive here as cache AND cache entries are mutated concurrently
                            final int itemsToRemove = cache.size() - maxCacheSize;
                            if ( itemsToRemove > 0 )
                            {
                                LOG.info("run(): Removing "+itemsToRemove+" items from cache.");
                                List<CacheEntrySnapshot> toRemove =
                                        cache.values().stream()
                                                .map(CacheEntrySnapshot::new)
                                                .sorted().collect( Collectors.toList());
                                int maxLen = toRemove.size() < itemsToRemove ? toRemove.size() : itemsToRemove;
                                if ( maxLen > 0 )
                                {
                                    toRemove = toRemove.subList( 0, maxLen );
                                    final List<CacheEntry> entries = toRemove.stream().map( x -> x.entry ).collect( Collectors.toList() );
                                    cache.values().removeAll( entries );
                                }
                            }
                        }
                    }
                }
            });

    public PatternLogFormatterCache(int maxCacheSize)
    {
        this.maxCacheSize = maxCacheSize;
    }

    public PatternLogFormatter get(String pattern) {

        CacheEntry entry = cache.get( pattern );
        if ( entry == null ) {
            entry = new CacheEntry( pattern );
            final CacheEntry previous = cache.putIfAbsent( pattern, entry );
            if ( previous != null ) {
                previous.usageCount.incrementAndGet();
            }
        }
        return entry.formatter;
    }

    @PostConstruct
    public void afterPropertiesSet() {
        cacheCleaner.startThread();
    }

    @PreDestroy
    public void destroy() throws InterruptedException
    {
        cacheCleaner.stopThread();
    }
}