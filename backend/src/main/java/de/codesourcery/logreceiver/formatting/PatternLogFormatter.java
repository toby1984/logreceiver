package de.codesourcery.logreceiver.formatting;

import de.codesourcery.logreceiver.SyslogMessage;

import java.util.ArrayList;
import java.util.List;

public class PatternLogFormatter implements ILogFormatter
{
    public static enum Field {

    }
    private static final Transformer YEAR_FIELD = msg -> leftPad( Integer.toString( msg.getTimestamp().getYear() ), '0', 4 );
    private static final Transformer MONTH_FIELD = msg -> leftPad( Integer.toString( msg.getTimestamp().getMonthValue() ), '0', 2 );
    private static final Transformer DAY_FIELD = msg -> leftPad( Integer.toString( msg.getTimestamp().getDayOfMonth() ), '0', 2 );
    private static final Transformer HOUR_FIELD = msg -> leftPad( Integer.toString( msg.getTimestamp().getHour() ), '0', 2 );
    private static final Transformer MINUTE_FIELD = msg -> leftPad( Integer.toString( msg.getTimestamp().getMinute() ), '0', 2 );
    private static final Transformer SECOND_FIELD = msg -> leftPad( Integer.toString( msg.getTimestamp().getSecond() ), '0', 2 );
    private static final Transformer NANO_FIELD = msg -> Integer.toString( msg.getTimestamp().getNano() );
    private static final Transformer MSG_FIELD = msg -> msg.message;
    private static final Transformer PRIORITY_FIELD = msg -> leftPad( Short.toString( msg.priority ), '0', 3 );
    private static final Transformer PROTO_HOSTNAME_FIELD = msg -> msg.hostName;
    private static final Transformer SD_PARAM_FIELD = msg ->
    {
        if ( msg.getParamCount() == 0 )
        {
            return null;
        }
        final StringBuilder tmp = new StringBuilder();
        for (int i = 0, len2 = msg.getParamCount(); i < len2; i++)
        {
            tmp.append( msg.params[i].toString() );
            if ( (i + 1) < len2 )
            {
                tmp.append( ',' );
            }
        }
        return tmp.toString();
    };
    private static final Transformer APPNAME_FIELD = msg -> msg.appName;
    private static final Transformer IP_FIELD = msg -> msg.host.ip.getHostAddress();
    private static final Transformer PROCID_FIELD = msg -> msg.procId;
    private static final Transformer MSGID_FIELD = msg -> msg.msgId;
    private static final Transformer DNS_NAME_FIELD = msg ->
    {
        try
        {
            return msg.host.ip.getHostName();
        } catch (Exception e)
        {
            return null;
        }
    };
    private static final Transformer TIMEZONE_FIELD = msg ->
    {
        if ( msg.tzHours < 0 ) {
            return "-"+leftPad( Integer.toString(-1*msg.tzHours),'0',2)+leftPad( Integer.toString(-1*msg.tzMinutes),'0',2);
        }
        return "+"+leftPad( Byte.toString(msg.tzHours),'0',2)+leftPad( Byte.toString(msg.tzMinutes),'0',2);
    };

    public final String pattern;

    private interface Transformer {
        String transform(SyslogMessage msg);
    }

    private final Transformer[] transformers;

    private PatternLogFormatter(String pattern) {
        if ( pattern == null || pattern.isBlank() ) {
            throw new IllegalArgumentException("Pattern must not be NULL or blank and at least 2 characters long");
        }
        this.pattern = pattern;
        this.transformers = compile(pattern);
    }

    private Transformer[] compile(String pattern) {

        final List<Transformer> result = new ArrayList<>();
        int start = 0;
        int end = 0;
        boolean quoted = false;
        for ( int len = pattern.length() ; end < len ; end++ ) {
            char c = pattern.charAt(end);

            if ( quoted ) {
                quoted = false;
                continue;
            }
            if ( c == '%' )
            {
                if ( (end+1) == len ) {
                    throw new IllegalArgumentException( "Illegal pattern at "+end );
                }
                char next = pattern.charAt(end+1);
                if ( next == '%')
                {
                    quoted = true;
                    end++;
                    continue;
                }
                final Transformer func = getTransformer( next );
                if ( func == null ) {
                    throw new IllegalArgumentException("Unrecognized pattern %"+next+" at "+end);
                }
                if ( start < end ) {
                    final String s = pattern.substring( start,end );
                    result.add( msg -> s );
                }
                start = end+2;
                end++; // skip character after % that we just processed
                result.add( func );
            }
        }
        if ( start < end )
        {
            final String s = pattern.substring( start,end );
            result.add( msg -> s );
        }
        return result.toArray( new Transformer[0] );
    }

    private Transformer getTransformer(char next)
    {
        switch( next )
        {
            case 'Y': return YEAR_FIELD;
            case 'm': return MONTH_FIELD;
            case 'd': return DAY_FIELD;
            case 'H': return HOUR_FIELD;
            case 'M': return MINUTE_FIELD;
            case 's': return SECOND_FIELD;
            case 'S': return NANO_FIELD;
            case 't': return MSG_FIELD;
            case 'p': return PRIORITY_FIELD;
            case 'h': return PROTO_HOSTNAME_FIELD;
            case 'P': return SD_PARAM_FIELD;
            case 'a': return APPNAME_FIELD;
            case 'i': return IP_FIELD;
            case 'c': return PROCID_FIELD;
            case 'I': return MSGID_FIELD;
            case 'D': return DNS_NAME_FIELD;
            case 'Z': return TIMEZONE_FIELD;
        }
        return null;
    }

    private static String leftPad(String input, char padChar, int len)
    {
        int delta = len - input.length();
        if ( delta <= 0 ) {
            return input;
        }
        final StringBuilder result = new StringBuilder();
        for ( ; delta > 0 ; delta-- ) {
            result.append(padChar);
        }
        result.append(input);
        return result.toString();
    }

    public static PatternLogFormatter ofPattern(String pattern) {
        return new PatternLogFormatter( pattern );
    }

    @Override
    public String format(SyslogMessage message)
    {
        final StringBuilder buffer = new StringBuilder();
        final Transformer[] functions = this.transformers;
        for (int i = 0, functionsSize = functions.length ; i < functionsSize; i++)
        {
            final String value = functions[i].transform( message );
            if ( value != null )
            {
                buffer.append( value );
            }
        }
        return buffer.toString();
    }
}