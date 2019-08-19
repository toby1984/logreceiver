package de.codesourcery.logreceiver.formatting;

import de.codesourcery.logreceiver.entity.SyslogMessage;

public interface ILogFormatter
{
    String format(SyslogMessage message);
}
