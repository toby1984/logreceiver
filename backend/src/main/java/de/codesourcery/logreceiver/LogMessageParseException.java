package de.codesourcery.logreceiver;

public class LogMessageParseException extends RuntimeException
{
    public final int offset;

    public LogMessageParseException(String message)
    {
        super( message );
        this.offset = -1;
    }

    public LogMessageParseException(String message,int offset)
    {
        super( message + (offset < 0 ? "" : " at offset "+offset) );
        this.offset = offset;
    }

    public LogMessageParseException(String message, Throwable cause)
    {
        super( message, cause );
        this.offset = -1;
    }

    public LogMessageParseException(String message, int offset,Throwable cause)
    {
        super( message + (offset < 0 ? "" : " at offset "+offset), cause );
        this.offset = offset;
    }
}

