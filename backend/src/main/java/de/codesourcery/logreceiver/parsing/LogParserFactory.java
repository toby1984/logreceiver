package de.codesourcery.logreceiver.parsing;

import de.codesourcery.logreceiver.filtering.FilterCallbackHelper;
import de.codesourcery.logreceiver.logstorage.ILogStorage;
import de.codesourcery.logreceiver.storage.IHostManager;

public class LogParserFactory
{
    private final ILogStorage writer;
    private final IHostManager hostManager;

    public LogParserFactory(ILogStorage writer, IHostManager hostManager)
    {
        this.writer = writer;
        this.hostManager = hostManager;
    }

    public ILogParser get()
    {
        return new RFC5424Parser(hostManager,writer);
    }
}
