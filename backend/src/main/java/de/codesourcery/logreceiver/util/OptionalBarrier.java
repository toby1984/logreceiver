package de.codesourcery.logreceiver.util;

/**
 * A thread barrier where threads can wait for some common
 * event to occur and will all be woken up when this happens
 * <b>or</b> if the lock is {@link #destroy() destroyed}.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class OptionalBarrier
{
    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private boolean destroyed;

    public void destroy()
    {
        synchronized (LOCK) {
            destroyed = true;
            wakeAll();
        }
    }

    public void wakeAll()
    {
        synchronized (LOCK) {
            LOCK.notifyAll();
        }
    }

    /**
     *
     * @throws InterruptedException
     * @throws IllegalStateException if invoked on a destroyed lock or if the thread got woken up by barrier destruction
     */
    public void await() throws InterruptedException, IllegalStateException
    {
        synchronized (LOCK)
        {
            if ( destroyed ) {
                throw new IllegalStateException("Invoked during/after shutdown");
            }
            LOCK.wait();

            if ( destroyed ) {
                throw new IllegalStateException("Shutdown in progress");
            }
        }
    }
}
