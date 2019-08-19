package de.codesourcery.logreceiver.logstorage;

import de.codesourcery.logreceiver.entity.Host;

import java.time.ZonedDateTime;

public interface ISQLLogStorage
{
    void store(Host host, ZonedDateTime timestamp, String sql);
}
