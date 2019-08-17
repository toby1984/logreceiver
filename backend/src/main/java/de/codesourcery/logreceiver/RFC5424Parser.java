package de.codesourcery.logreceiver;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.function.IntPredicate;

public class RFC5424Parser implements ILogParser
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( RFC5424Parser.class.getName() );

    private final Scanner scanner = new Scanner();
    private final ILogConsumer logWriter;

    private final SyslogMessage message = new SyslogMessage();

    private final StringBuilder buffer = new StringBuilder();

    private final IHostManager hostManager;

    public RFC5424Parser(IHostManager hostManager,ILogConsumer logWriter) {
        this.logWriter = logWriter;
        this.hostManager = hostManager;
    }

    @Override
    public void parse(InetAddress sender,InputStream in)
    {
        scanner.setData( new InputStreamReader( in ) );
        message.reset();
        message.address = sender;
        buffer.setLength( 0 );
        try
        {
            parse();
            logWriter.store(message);
        }
        catch(Throwable t)
        {
            LOG.fatal("parse(): Message might have been lost",t);
            if ( t instanceof Error) {
                throw t;
            }
            throw (RuntimeException) t;
        }
    }

    private void fail(String message) {
        fail(message,scanner.offset());
    }

    private void fail(String message,int offset) {
        throw new LogMessageParseException( message, offset);
    }

    private void parse()
    {
        parseHeader();
        parseSP();
        parseStructuredData();
        if ( maybeConsume( ' ' ) )
        {
            // skip leading whitespace
            while ( maybeConsume( ' ' ) );
            if ( ! scanner.eof() )
            {
                parseMessage();
            }
        }
    }

    private void parseMessage()
    {
        buffer.setLength(0);
        while ( ! scanner.eof() ) {
            buffer.append( scanner.next() );
        }
        message.message = buffer.toString();
    }

    private void parseStructuredData()
    {
        if ( maybeParseNilValue() ) {
            return;
        }
        int count = 0;
        while ( parseSDElement() ) {
            count++;
        }
        if ( count == 0 ) {
            fail("Expected SD-Element");
        }
    }

    private boolean parseSDElement() {

        if ( ! maybeConsume( '[' ) ) {
            return false;
        }
        final String id = parseSDId();

        final SDParam param = new SDParam( id );

        while (  maybeConsume( ' ' ) )
        {
            parseSDParam( param );
        }
        consume(']' );

        message.addParam(param );
        return true;
    }

    private void parseSDParam(SDParam parent) {
        String key = parseSDName();
        consume( '=' );
        consume( (char) 34 );
        buffer.setLength( 0 );
        while ( ! scanner.eof() ) {
            char c = scanner.peek();
            if ( c == (char) 34 ) {
                break;
            }
            scanner.next();
            buffer.append( c );
        }
        consume( (char) 34 );
        String value = buffer.toString();
        parent.addParam(key,value);
    }

    private String parseSDId() {
        return parseSDName();
    }

    private String parseSDName()
    {
        int offset = scanner.offset();
        final String string = parseUSAscii( 32 );
        for ( int i = 0, len = string.length() ; i < len ; i++ ) {
            char c = string.charAt( i );
            if ( c == '=' || c == ' ' || c == ']' || c == '"' ) {
                fail("Invalid character '"+c+"'", offset+i );
            }
        }
        return string;
    }

    private void parseHeader()
    {
        parsePRI();

        parseVersion();

        parseSP();
        parseTimestamp();

        parseSP();
        parseHostname();

        parseSP();
        parseAppName();

        parseSP();
        parsePROCID();

        parseSP();
        parseMSGID();
    }

    private void parseMSGID()
    {
        if ( maybeParseNilValue() ) {
            return;
        }
        message.msgId = parseUSAscii( 32 );
    }

    private void parsePROCID() {
        if ( maybeParseNilValue() ) {
            return;
        }
        message.procId = parseUSAscii( 128 );
    }

    private void parseHostname() {
        if ( maybeParseNilValue() ) {
            message.host = hostManager.getOrCreateHost( message.address, null );
            return;
        }
        message.hostName = parseUSAscii( 255 );
        message.host = hostManager.getOrCreateHost( message.address, message.hostName );
    }

    private void parseAppName() {
        if ( maybeParseNilValue() ) {
            return;
        }
        message.appName = parseUSAscii( 48 );
    }

    private String parseUSAscii(int maxLength) {

        buffer.setLength( 0 );
        int count = 0;
        do {
            if ( scanner.eof() ) {
                break;
            }
            char c = scanner.peek();
            if ( c < 33 || c > 126 ) {
                break;
            }
            scanner.next();
            buffer.append(c);
            count++;
        } while( count < maxLength );
        if ( count < 1 ) {
            fail( "Expected at least one US ASCII character", scanner.offset() );
        }
        return buffer.toString();
    }

    private void parseTimestamp()
    {
        if ( maybeParseNilValue() ) {
            return;
        }

        // YYYY
        message.year = parseFixedLengthNumber("year (YYYY)",4, num -> num > 0);
        consume('-' );
        message.month = parseFixedLengthNumber("month (MM)",2, num -> num > 0 && num < 13);
        consume('-' );
        message.dayOfMonth = parseFixedLengthNumber("monthday (DD)",2, num -> num > 0 && num < 32);

        consume('T' );

        message.hour = parseFixedLengthNumber("hour (HH)",2, num -> num >= 0 && num < 24);
        consume(':');
        message.minute = parseFixedLengthNumber("minute (MM)",2, num -> num >= 0 && num < 60);
        consume(':');
        message.second = parseFixedLengthNumber("seconds (SS)",2, num -> num >= 0 && num < 60);

        if ( maybeConsume( '.' ) ) {
            // fraction
            message.secondFrag = parseNumber( 6, "time sec-frag" );
        }

        if ( maybeConsume( 'Z' ) ) {
            message.posTZ = true;
            message.tzHours = 0;
            message.tzMinutes = 0;
        }
        else
        {
            if ( maybeConsume( '+' ) )
            {
                message.posTZ = true;
            }
            else
            {
                message.posTZ = !maybeConsume( '-' );
            }

            message.tzHours = parseFixedLengthNumber( "TZ offset hours", 2, x -> x >= 0 && x < 24 );
            consume(':');
            message.tzMinutes = parseFixedLengthNumber( "TZ offset minutes", 2, x -> x >= 0 && x < 60 );
        }
    }

    private static boolean isNoDigit(char c) {
        return c < '0' || c > '9';
    }

    private int parseNumber(int maxLength,String what)
    {
        return parseNumber(1,maxLength,what);
    }

    private int parseNumber(int minLength,int maxLength,String what)
    {
        final int startOffset = scanner.offset();
        int result = 0;
        int digitCount = 0;
        do
        {
            if ( scanner.eof() ) {
                break;
            }
            final char c = scanner.peek();
            if ( isNoDigit( c ) ) {
                break;
            }
            result *= 10;
            result += c - '0';
            scanner.next();
            digitCount++;
        } while ( digitCount < maxLength );

        if ( digitCount < minLength ) {
            fail("Expected at least "+minLength+" digits of "+what,startOffset);
        }
        return result;
    }

    private boolean maybeParseNilValue()
    {
        if ( ! scanner.eof() && scanner.peek() == '-' ) {
            scanner.next();
            return true;
        }
        return false;
    }

    private void parseVersion()
    {
        final int version = parseNumber( 3, "version" );
        // TODO: protocol version is currently being ignored ?
    }

    private int parseFixedLengthNumber(String what, int len, IntPredicate validator)
    {
        int offset = scanner.offset();
        int number = parseNumber(len,len, what);
        if ( ! validator.test( number ) ) {
            fail("Number "+number+" is out-of-range for "+what,offset);
        }
        return number;
    }

    private void parsePRI() {
        consume('<' );
        parsePRIVal();
        consume('>' );
    }

    private void parsePRIVal()
    {
        final int start = scanner.offset();
        final int prio = parseNumber( 3, "priority value" );
        if ( prio > 191 ) {
            fail("Priority must be [0...191] but was "+prio,start);
        }
        message.priority = (short) prio;
    }

    private void parseSP() {
        consume(' ');
    }

    private boolean maybeConsume(char c)
    {
        return scanner.consume(c);
    }

    private void consume(char c)
    {
        if ( ! scanner.consume( c ) ) {
            fail("Expected '"+c+"'");
        }
    }
}
