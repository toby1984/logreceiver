package de.codesourcery.logreceiver;

public class LogWriterAdapter implements ILogWriter
{
    private final ILogWriter delegate;

    public LogWriterAdapter(ILogWriter delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public void store(SyslogMessage message)
    {
        delegate.store(message);
    }
}