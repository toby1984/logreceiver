package de.codesourcery.logreceiver;

import java.io.InputStream;

/**
 * Consumes log messages.
 *
 * @author tobias.gierke@voipfuture.com
 */
@FunctionalInterface
public interface ILogConsumer
{
    void store(SyslogMessage message);
}
