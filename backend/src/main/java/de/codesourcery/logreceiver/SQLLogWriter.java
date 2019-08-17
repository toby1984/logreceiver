package de.codesourcery.logreceiver;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.stream.Stream;

public class SQLLogWriter implements ILogConsumer
{
    private static final String SQL_NULL = "null";
    private static final char COL_DELIMITER = '|';
    public static final char ROW_DELIMITER = '\n';

    private final StringBuilder buffer = new StringBuilder();

    private final ILogStorage storage;
    private final IHostManager hostIdResolver;

    private Host host;
    private ZonedDateTime timestamp;

    private int currentField;

    public enum Field
    {
        PRIORITY(0),
        TIMESTAMP(1),
        TIMESTAMP_FRACTION(2),
        HOSTNAME(3),
        APPNAME(4),
        PROC_ID(5),
        MSG_ID(6),
        PARAMS(7),
        MESSAGE(8);

        public final int index;

        Field(int idx) {
            this.index = idx;
        }
    }

    @Override
    public void store(SyslogMessage message)
    {
        beginMessage();
        host = hostIdResolver.getOrCreateHost(message.address,message.hostName);
        setPriority(message.priority);
        setTimestamp(message);
        setHostname(message);
        setAppname(message.appName);
        setProcID(message.procId);
        setMsgId(message.msgId);
        setParams(message);
        setMessage(message.message);
        endMessage();
    }

    private static final Field[] fields = Stream.of( Field.values() ).sorted( Comparator.comparingInt(a -> a.index ) ).toArray( Field[]::new );

    public SQLLogWriter(ILogStorage storage, IHostManager hostIdResolver) {
        this.storage = storage;
        this.hostIdResolver = hostIdResolver;
    }

    public void beginMessage()
    {
        if ( currentField != 0 || buffer.length() != 0 ) {
            throw new IllegalStateException( "endMessage() not called?" );
        }
        host = null;
        timestamp = null;
    }

    public void endMessage()
    {
        if ( currentField != fields.length )
        {
            writeNullValues( fields.length - currentField );
        }

        storage.store( host, timestamp, buffer.toString() );
        currentField = 0;
        buffer.setLength( 0 );
    }

    private void writeNullValues(int nullValueCount)
    {
        if ( nullValueCount == 0 ) {
            return;
        }
        if ( buffer.length() > 0 ) {
            buffer.append(COL_DELIMITER);
        }
        for ( int i = nullValueCount ; i > 0 ; i-- )
        {
            if ( i == 1 )
            {
                buffer.append(SQL_NULL);
            } else {
                buffer.append(SQL_NULL).append(COL_DELIMITER);
            }
        }
    }

    private void writeLong(long value)
    {
        if ( buffer.length() > 0 )
        {
            buffer.append(COL_DELIMITER);
        }
        buffer.append( value );
    }

    private void writeString(String value)
    {
        if ( buffer.length() > 0 )
        {
            buffer.append(COL_DELIMITER);
        }
        appendEscaped( value );
    }

    private void advanceTo(Field field)
    {
        if ( currentField != field.index )
        {
            if ( field.index < currentField ) {
                throw new IllegalStateException("Cannot go backwards");
            }
            writeNullValues( field.index - currentField );
            currentField = field.index;
        }
    }

    public void setPriority(int prio)
    {
        advanceTo( Field.PRIORITY );
        writeLong( prio );
        currentField++;
    }

    public void setTimestamp(SyslogMessage message)
    {
        advanceTo( Field.TIMESTAMP );

        if ( buffer.length() > 0 )
        {
            buffer.append(COL_DELIMITER);
        }

        timestamp = message.getTimestamp();

        buffer.append( message.year ).append('-');
        buffer.append( message.month ).append('-');
        buffer.append( message.dayOfMonth ).append(' ');

        buffer.append( message.hour ).append(':');
        buffer.append( message.minute ).append(':');
        buffer.append( message.second );

        buffer.append( message.tzHours >= 0 ? '+' : '-' );
        buffer.append( message.tzHours ).append(':').append( message.tzMinutes );

        currentField++;

        advanceTo( Field.TIMESTAMP_FRACTION );
        buffer.append(COL_DELIMITER);
        buffer.append( message.secondFrag );
        currentField++;
    }

    public void setHostname(SyslogMessage message)
    {
        final String string = message.hostName;
        if ( string != null && string.length() > 0 )
        {
            host = hostIdResolver.getOrCreateHost( message.address, string );
            advanceTo( Field.HOSTNAME );
            writeLong( host.id );
            currentField++;
        }
    }

    public void setAppname(String app)
    {
        if ( app != null && app.length() > 0 )
        {
            advanceTo( Field.APPNAME );
            writeString( app );
            currentField++;
        }
    }

    public void setProcID(String procId)
    {
        if ( procId != null && procId.length() > 0 )
        {
            advanceTo( Field.PROC_ID );
            writeString( procId );
            currentField++;
        }
    }

    public void setMsgId(String msgId)
    {
        if ( msgId != null && msgId.length() > 0 )
        {
            advanceTo( Field.MSG_ID );
            writeString( msgId );
            currentField++;
        }
    }

    private void appendJSON(String string) {

        if ( string == null || string.length() == 0 ) {
            buffer.append("null");
            return;
        }
        for ( int i = 0, len = string.length() ; i < len ; i++ )
        {
            final char c = string.charAt( i );
            if ( c < 32 ) { // replace all special characters with SP
                buffer.append(' ');
                continue;
            }
            appendEscaped(c);
        }
    }

    public void setParams(SyslogMessage message)
    {
        final int count = message.getParamCount();
        if ( count > 0 )
        {
            advanceTo( Field.PARAMS );

            if ( buffer.length() > 0) {
                buffer.append(COL_DELIMITER);
            }

            buffer.append("{\"data\" : [");
            for ( int i = count-1 ; i >= 0 ; i--)
            {
                final SDParam value = message.params[i];
                buffer.append('{');

                buffer.append("\"id\" : ");
                appendJSON( value.id );

                buffer.append(",\"params\":");
                if ( value.paramCount() > 0 ) {

                    buffer.append('{');
                    for ( int j = value.paramCount()-1 ; j >= 0 ; j--) {
                        appendJSON( value.paramNames[j] );
                        buffer.append(':');
                        appendJSON( value.paramValues[j] );
                        if ( j != 0 ) {
                            appendEscaped(',');
                        }
                    }
                    buffer.append('}');
                } else {
                    buffer.append("null");
                }
                buffer.append("}");
                if ( i != 0 ) {
                    appendEscaped(',');
                }
            }
            buffer.append("]}");
            currentField++;
        }
    }

    public void setMessage(String message)
    {
        if ( message == null ) {
            return;
        }

        advanceTo( Field.MESSAGE );
        if ( buffer.length() > 0 )
        {
            buffer.append(COL_DELIMITER);
        }
        appendEscaped(message);
        currentField++;
    }

    private void appendEscaped(String input)
    {
        for ( int i = 0 , len = input.length() ; i < len ; i++ )
        {
            appendEscaped( input.charAt(i) );
        }
    }

    private void appendEscaped(char c)
    {
        switch(c)
        {
            case '\\': buffer.append("\\\\"); return;
            case '\b': buffer.append("\\b"); return;
            case '\f': buffer.append("\\f"); return;
            case '\n': buffer.append("\\n"); return;
            case '\r': buffer.append("\\r"); return;
            case '\t': buffer.append("\\t"); return;
            case COL_DELIMITER: buffer.append("\\").append(COL_DELIMITER); return;
            case 11: buffer.append("\\v"); return;
        }
        buffer.append(c);
    }
}
