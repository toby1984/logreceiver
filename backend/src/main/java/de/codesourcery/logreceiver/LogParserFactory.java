package de.codesourcery.logreceiver;

import java.util.function.Supplier;

public class LogParserFactory implements Supplier<ILogParser>
{
    private final ILogConsumer writer;


    public LogParserFactory(ILogConsumer writer)
    {
        this.writer = writer;
    }

    @Override
    public ILogParser get()
    {
        return new RFC5424Parser(writer);
    }
}
