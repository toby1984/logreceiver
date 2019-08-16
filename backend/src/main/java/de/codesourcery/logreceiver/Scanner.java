package de.codesourcery.logreceiver;

import java.io.IOException;
import java.io.Reader;

public class Scanner implements IScanner
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( Scanner.class.getName() );

    private static final boolean DEBUG = false;

    private Reader reader;

    private char[] buffer = new char[1024];
    private int bufferPtr=0;
    private int availableBytes;

    private boolean eof;
    private int offset;

    public Scanner() {
    }

    @Override
    public void setData(Reader reader)
    {
        this.reader = reader;
        availableBytes = 0;
        bufferPtr = 0;
        eof = false;
        offset = 0;
        fillBuffer();
    }

    private void fillBuffer()
    {
        if ( eof ) {
            availableBytes = 0;
            return;
        }
        int value = 0;
        try
        {
            value = reader.read(buffer);
            if ( value <= 0 )
            {
                eof = true;
                availableBytes = 0;
                return;
            }
            availableBytes = value;
            bufferPtr = 0;
        }
        catch (IOException e)
        {
            availableBytes = 0;
            eof = true;
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean eof()
    {
        if ( ! eof )
        {
            int remaining = availableBytes - bufferPtr;
            if ( remaining <= 0 )
            {
                fillBuffer();
            }
        }
        return eof;
    }

    @Override
    public char next()
    {
        if ( eof() )
        {
            throw new IllegalStateException("Already at EOF");
        }
        if ( DEBUG ) {
            if ( LOG.isTraceEnabled() ) {
                LOG.trace("next(): "+buffer[bufferPtr]);
            }
        }
        offset++;
        return buffer[bufferPtr++];
    }

    @Override
    public char peek()
    {
        if ( eof() )
        {
            throw new IllegalStateException("Already at EOF");
        }
        return buffer[bufferPtr];
    }

    @Override
    public int offset()
    {
        return offset;
    }
}