package de.codesourcery.logreceiver;

import java.io.Reader;

public interface IScanner
{
    void setData(Reader reader);

    boolean eof();

    char next();

    char peek();

    int offset();
}
