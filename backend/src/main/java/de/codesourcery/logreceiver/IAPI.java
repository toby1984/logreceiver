package de.codesourcery.logreceiver;

import java.time.ZonedDateTime;
import java.util.List;

public interface IAPI
{
    List<SyslogMessage> getMessages(Host host, ZonedDateTime referenceDate,boolean ascending,int maxCount);

    List<SyslogMessage> getMessages(Host host, long entryId,boolean ascending,int maxCount);

    List<Host> getAllHosts();
}
