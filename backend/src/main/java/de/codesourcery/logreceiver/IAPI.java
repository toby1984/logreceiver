package de.codesourcery.logreceiver;

import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.entity.SyslogMessage;
import de.codesourcery.logreceiver.filtering.IFilterCallback;

import java.time.ZonedDateTime;
import java.util.List;

public interface IAPI
{
    enum PagingDirection {
        FORWARD_IN_TIME,
        BACKWARD_IN_TIME
    }

    List<SyslogMessage> subscribe(Host host, IFilterCallback callback, int maxCount);

    List<SyslogMessage> getMessages(Host host, IFilterCallback callback, PagingDirection direction, long refLogEntryId, int maxCount);

    void unsubscribe(Host host,IFilterCallback callback);

    List<Host> getAllHosts();

    Host getHost(long hostId);
}