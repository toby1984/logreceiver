package de.codesourcery.logreceiver.parsing;

import java.io.InputStream;
import java.net.InetAddress;

public interface ILogParser
{
    void parse(InetAddress sender,InputStream in);
}
