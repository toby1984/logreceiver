package de.codesourcery.logreceiver.parsing;

import java.io.Reader;

public interface IScanner
{
    void setData(Reader reader);

    boolean eof();

    char next();

    char peek();

    boolean consume(char c);

    int offset();
}
