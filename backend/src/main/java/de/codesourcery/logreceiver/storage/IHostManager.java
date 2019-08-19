package de.codesourcery.logreceiver.storage;

import de.codesourcery.logreceiver.entity.Host;

import java.net.InetAddress;
import java.util.List;

public interface IHostManager
{
    Host getOrCreateHost(InetAddress address, String hostname);

    Host getHost(long id);

    List<Host> getAllHosts();

    Host getHost(InetAddress address);
}
