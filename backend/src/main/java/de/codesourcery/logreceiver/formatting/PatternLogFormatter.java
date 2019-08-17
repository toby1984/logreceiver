package de.codesourcery.logreceiver.formatting;

import de.codesourcery.logreceiver.SyslogMessage;

import java.util.ArrayList;
import java.util.List;

public class PatternLogFormatter implements ILogFormatter
{
    public final String pattern;

    private interface Transformer {
        String transform(SyslogMessage msg);
    }
    private final Transformer[] transformers;

    private PatternLogFormatter(String pattern) {
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
            if ( c == '%' && (end+1) < len )
            {
                char next = pattern.charAt(end+1);
                if ( next == '%' && ! quoted)
                {
                    quoted = true;
                    continue;
                }
                if ( quoted ) {
                    quoted = false;
                    continue;
                }
                final Transformer func = getTransformer( next );
                if ( func != null )
                {
                    if ( start < end ) {
                        final String s = pattern.substring( start,end-1 );
                        result.add( msg -> s );
                        start = end+2;
                    }
                    end++; // skip character after % that we just processed
                    result.add( func );
                }
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
            case 'Y':
                return msg -> leftPad(Integer.toString(msg.getTimestamp().getYear()),'0',4);
            case 'm':
                return msg -> leftPad( Integer.toString( msg.getTimestamp().getMonthValue() ), '0', 2 );
            case 'd':
                return msg -> leftPad(Integer.toString(msg.getTimestamp().getDayOfMonth()),'0',2);
            case 'H':
                return msg -> leftPad(Integer.toString(msg.getTimestamp().getHour()),'0',2);
            case 'M':
                return msg -> leftPad(Integer.toString(msg.getTimestamp().getMinute()),'0',2);
            case 's':
                return msg -> leftPad(Integer.toString(msg.getTimestamp().getSecond()),'0',2);
            case 'S':
                return msg -> Integer.toString( msg.getTimestamp().getNano() );
            case 't':
                return msg -> msg.message;
            case 'p':
                return msg -> leftPad(Short.toString(msg.priority),'0',3);
            case 'h':
                return msg -> msg.hostName;
            case 'P':
                return msg -> {
                    if ( msg.getParamCount() == 0 ) {
                        return null;
                    }
                    final StringBuilder tmp = new StringBuilder();
                    for ( int i = 0, len2 = msg.getParamCount() ; i < len2 ; i++ ) {
                        tmp.append( msg.params[i].toString() );
                        if ( (i+1) < len2 ) {
                            tmp.append(',');
                        }
                    }
                    return tmp.toString();
                };
            case 'a':
                return msg -> msg.appName;
            case 'i':
                return msg -> msg.host.ip.getHostAddress();
            case 'c':
                return msg -> msg.procId;
            case 'I':
                return msg -> msg.msgId;
            case 'D':
                return msg ->
                {
                    try {
                        return msg.host.ip.getHostName();
                    } catch(Exception e) {
                        return null;
                    }
                };
        }
        return null;
    }

    private static String leftPad(String input, char padChar, int len)
    {
        int delta = input.length() - len;
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