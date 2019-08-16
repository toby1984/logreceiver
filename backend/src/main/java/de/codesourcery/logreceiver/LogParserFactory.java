package de.codesourcery.logreceiver;

import java.util.function.Supplier;

public class LogParserFactory implements Supplier<ILogParser>
{
    private final ILogWriter writer;


    public LogParserFactory(ILogWriter writer)
    {
        this.writer = writer;
    }

    @Override
    public ILogParser get()
    {
        return new RFC5424Parser(writer);
    }
}
