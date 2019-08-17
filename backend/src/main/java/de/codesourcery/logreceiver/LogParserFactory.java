package de.codesourcery.logreceiver;

import java.util.function.Supplier;

public class LogParserFactory implements Supplier<ILogParser>
{
    private final ILogConsumer writer;
    private final IHostManager hostManager;


    public LogParserFactory(ILogConsumer writer, IHostManager hostManager)
    {
        this.writer = writer;
        this.hostManager = hostManager;
    }

    @Override
    public ILogParser get()
    {
        return new RFC5424Parser(hostManager,writer);
    }
}
