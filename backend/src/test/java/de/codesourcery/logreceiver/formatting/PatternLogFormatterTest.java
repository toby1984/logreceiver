package de.codesourcery.logreceiver.formatting;

import de.codesourcery.logreceiver.Host;
import de.codesourcery.logreceiver.SDParam;
import de.codesourcery.logreceiver.SyslogMessage;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PatternLogFormatterTest
{
    private interface Throwing {
        void run() throws UnknownHostException;
    }

    @Test
    public void testValidPatterns() throws UnknownHostException
    {
        assertMatch("blubb1974","blubb%Y");
        assertMatch("1974blubb","%Yblubb");
        assertMatch("blah1974blubb","blah%Yblubb");
        assertMatch("-0330","%Z");

        assertMatch("1974-12-11 10:09:08.7-0330 dns.google(8.8.8.8) message","%Y-%m-%d %H:%M:%s.%S%Z %D(%i) %t");
        assertMatch("1974","%Y");
        assertMatch("12","%m");
        assertMatch("11","%d");
        assertMatch("10","%H");
        assertMatch("09","%M");
        assertMatch("08","%s");
        assertMatch("7","%S");
        assertMatch("message","%t");
        assertMatch("123","%p");
        assertMatch("hostname","%h");
        assertMatch("app","%a");
        assertMatch("msgid","%I");
        assertMatch("8.8.8.8","%i");
        assertMatch("procid","%c");
        assertMatch("dns.google","%D");
        assertMatch("a{b=c}","%P");
    }

    @Test
    public void testIllegalPatterns() {
        assertThrows( () -> eval( "%%" ) );
        assertThrows( () -> eval( null ) );
        assertThrows( () -> eval( "" ) );
        assertThrows( () -> eval( "    " ) );
        assertThrows( () -> eval( "%1" ) );
        assertThrows( () -> eval( "test%" ) );
        assertThrows( () -> eval( "test%1" ) );
    }

    private void assertThrows(Throwing r)
    {
        try {
            r.run();
            fail( "Should've thrown IllegalArgumentException" );
        }
        catch(IllegalArgumentException e) {
            // ok
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
            fail("Unexpected exception");
        }
    }

    private void assertMatch(String expected, String pattern) throws UnknownHostException
    {
        String actual = eval(pattern);
        assertEquals(expected,actual);
    }

    private String eval(String pattern) throws UnknownHostException
    {
        return PatternLogFormatter.ofPattern( pattern ).format( testMessage() );
    }

    private SyslogMessage testMessage() throws UnknownHostException
    {
        final SyslogMessage msg = new SyslogMessage();
        msg.year = 1974;
        msg.month = 12;
        msg.dayOfMonth = 11;
        msg.hour = 10;
        msg.minute = 9;
        msg.second = 8;
        msg.secondFrag = 7;
        msg.tzHours = -3;
        msg.tzMinutes= -30;

        msg.priority = 123;
        msg.procId = "procid";
        msg.appName = "app";
        msg.message = "message";
        msg.msgId = "msgid";
        msg.hostName = "hostname";

        final Host h = new Host();
        h.hostName = "hostname";
        h.ip = Inet4Address.getByAddress( new byte[] {8,8,8,8} );
        msg.host = h;

        final SDParam param = new SDParam( "a" );
        param.addParam( "b" , "c" );
        msg.addParam( param );
        return msg;
    }
}