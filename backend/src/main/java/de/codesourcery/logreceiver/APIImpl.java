package de.codesourcery.logreceiver;

import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.entity.SyslogMessage;
import de.codesourcery.logreceiver.filtering.FilterCallbackHelper;
import de.codesourcery.logreceiver.filtering.IFilterCallback;
import de.codesourcery.logreceiver.logstorage.MessageDAO;
import de.codesourcery.logreceiver.storage.IHostManager;

import java.util.List;

public class APIImpl implements IAPI
{
    private final IHostManager hostManager;
    private final MessageDAO dao;
    private FilterCallbackHelper callbackHelper;

    public APIImpl(IHostManager hostManager, MessageDAO dao,FilterCallbackHelper callbackHelper) {

        this.hostManager = hostManager;
        this.dao = dao;
        this.callbackHelper = callbackHelper;
    }

    @Override
    public List<SyslogMessage> subscribe(Host host, IFilterCallback callback, int maxCount)
    {
        callbackHelper.register(host.ip, callback );
        return dao.getLatestMessages( host, callback, maxCount);
    }

    @Override
    public List<SyslogMessage> getMessages(Host host, IFilterCallback callback, PagingDirection direction,
                                           long refLogEntryId, int maxCount)
    {
        return dao.getMessages( host,callback,direction,refLogEntryId,maxCount );
    }

    @Override
    public void unsubscribe(Host host, IFilterCallback callback)
    {
        callbackHelper.unregister(host.ip, callback );
    }

    @Override
    public List<Host> getAllHosts()
    {
        return hostManager.getAllHosts();
    }

    @Override
    public Host getHost(long hostId)
    {
        return hostManager.getHost( hostId );
    }
}