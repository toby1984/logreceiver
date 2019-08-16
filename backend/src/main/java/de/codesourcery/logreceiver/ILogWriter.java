package de.codesourcery.logreceiver;

import java.io.InputStream;

/**
 * Write log message.
 *
 * Note that fields need to be called in EXACTLY the following order
 * (it's ok to skip them if you don't have a value for it though).
 *
 * <code>
 * public int priority;
 * public SyslogTimestamp timestamp;
 * public String hostname;
 * public String appname;
 * public String procid;
 * public String msgid;
 * public SDParam[] params;
 * public String message;
 * </code>
 * @author tobias.gierke@voipfuture.com
 */
public interface ILogWriter
{
    void store(SyslogMessage message);
}
