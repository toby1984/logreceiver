package de.codesourcery.logreceiver;

import java.net.InetAddress;
import java.util.List;

public interface IHostManager
{
    Host getOrCreateHost(InetAddress address,String hostname);

    Host getHost(long id);

    List<Host> getAllHosts();
}
