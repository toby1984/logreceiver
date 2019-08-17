package de.codesourcery.logreceiver.formatting;

import de.codesourcery.logreceiver.SyslogMessage;

public interface ILogFormatter
{
    String format(SyslogMessage message);
}
