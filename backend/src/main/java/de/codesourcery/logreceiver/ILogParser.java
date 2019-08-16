package de.codesourcery.logreceiver;

import java.io.InputStream;
import java.net.InetAddress;

public interface ILogParser
{
    void parse(InetAddress sender,InputStream in);
}
