package de.codesourcery.logreceiver;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class EternalThread
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( EternalThread.class.getName() );

    public interface Context
    {
        boolean isCancelled();

        /**
         *
         * @param duration
         * @return <code>true</code> if sleep() call was executed, <code>false</code> if sleep() call
         * got skipped because this thread got cancelled in the meantime
         */
        boolean sleep(Duration duration);

        /**
         *
         * @param millis
         * @return <code>true</code> if sleep() call was executed, <code>false</code> if sleep() call
         * got skipped because this thread got cancelled in the meantime
         */
        boolean sleepMillis(long millis);
    }

    public interface Interruptable
    {
        default void cancel() {
        }

        void run(Context context) throws Exception;
    }

    private final String name;
    private Supplier<Interruptable> supplier;

    private final Object THREAD_LOCK = new Object();

    private volatile boolean shutdown;

    private MyThread thread;

    public EternalThread(String name, Supplier<Interruptable> supplier)
    {
        this.supplier = supplier;
        this.name = name;
    }

    public void startThread()
    {
        synchronized (THREAD_LOCK)
        {
            if ( ! shutdown && ( thread == null || ! thread.isAlive() ) ) {
                thread = new MyThread();
                thread.start();
            }
        }
    }

    public void stopThread() throws InterruptedException
    {
        synchronized (THREAD_LOCK)
        {
            shutdown = true;
            if ( thread != null && thread.isAlive() ) {
                thread.cancel();
            }
        }
    }

    private final class MyThread extends Thread implements Context
    {
        private final Object SLEEP_LOCK = new Object();

        private final Interruptable r = supplier.get();

        private final CountDownLatch stopped = new CountDownLatch( 1 );

        public MyThread()
        {
            super( name );
        }

        @Override
        public boolean isCancelled()
        {
            return shutdown;
        }

        public void cancel() throws InterruptedException
        {
            r.cancel();
            wakeUp();
            stopped.await();
        }

        public void wakeUp()
        {
            synchronized (SLEEP_LOCK) {
                SLEEP_LOCK.notifyAll();
            }
        }

        @Override
        public void run()
        {
            try
            {
                while ( !shutdown )
                {
                    r.run(this);
                }
            }
            catch (Throwable t)
            {
                LOG.error( "run(): Caught ", t );
                if ( t instanceof Error )
                {
                    throw (Error) t;
                }
            }
            finally
            {
                stopped.countDown();
                if ( !shutdown )
                {
                    LOG.warn( "run(): Thread died unexpectedly, restarting in 10 seconds" );
                    final Thread t = new Thread( () -> {

                        try
                        {
                            Thread.sleep( 10*1000 );
                        }
                        catch (InterruptedException e)
                        {
                            /* can't help it */
                        }
                        startThread();
                    } );
                    t.setName( name + "-restart-thread" );
                    t.setDaemon( true );
                    t.start();
                }
                else
                {
                    LOG.info( "run(): Orderly shutdown." );
                }
            }
        }

        @Override
        public boolean sleep(Duration duration)
        {
            return sleepMillis( duration.toMillis() );
        }

        @Override
        public boolean sleepMillis(long millis)
        {
            if ( shutdown )
            {
                return false;
            }
            synchronized( SLEEP_LOCK )
            {
                if ( shutdown )
                {
                    return false;
                }
                try
                {
                    SLEEP_LOCK.wait( millis );
                }
                catch (InterruptedException e)
                {
                    LOG.warn( "sleepMillis(): Interrupted" );
                }
                return true;
            }
        }
    }

    public void wakeUp()
    {
        thread.wakeUp();
    }

    public boolean isShutdown()
    {
        return shutdown;
    }
}

