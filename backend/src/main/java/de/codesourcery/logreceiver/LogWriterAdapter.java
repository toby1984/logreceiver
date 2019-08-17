package de.codesourcery.logreceiver;

public class LogWriterAdapter implements ILogConsumer
{
    private final ILogConsumer delegate;

    public LogWriterAdapter(ILogConsumer delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public void store(SyslogMessage message)
    {
        delegate.store(message);
    }
}