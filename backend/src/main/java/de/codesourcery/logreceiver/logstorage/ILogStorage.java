package de.codesourcery.logreceiver.logstorage;

import de.codesourcery.logreceiver.entity.SyslogMessage;

/**
 * Consumes log messages.
 *
 * @author tobias.gierke@voipfuture.com
 */
@FunctionalInterface
public interface ILogStorage
{
    void store(SyslogMessage message);
}
