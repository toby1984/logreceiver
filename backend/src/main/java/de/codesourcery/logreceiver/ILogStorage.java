package de.codesourcery.logreceiver;

import java.time.ZonedDateTime;

public interface ILogStorage
{
    void store(Host host,ZonedDateTime timestamp,String sql);
}
