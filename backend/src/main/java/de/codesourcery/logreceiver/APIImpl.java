package de.codesourcery.logreceiver;

import java.time.ZonedDateTime;
import java.util.List;

public class APIImpl implements IAPI
{
    private final IHostManager hostManager;
    private final MessageDAO dao;

    public APIImpl(IHostManager hostManager, MessageDAO dao) {

        this.hostManager = hostManager;
        this.dao = dao;
    }

    @Override
    public List<SyslogMessage> getMessages(Host host, ZonedDateTime referenceDate, boolean ascending, int maxCount)
    {
        return dao.getMessages(host,referenceDate,ascending,maxCount);
    }

    @Override
    public List<Host> getAllHosts()
    {
        return hostManager.getAllHosts();
    }
}
