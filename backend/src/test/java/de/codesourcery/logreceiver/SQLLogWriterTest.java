package de.codesourcery.logreceiver;

import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class SQLLogWriterTest
{
    private SQLLogWriter writer;
    private MockStorage storage;

    public static final class MockStorage implements ILogStorage {

        public final StringBuilder buffer = new StringBuilder();
        
        @Override
        public void store(Host host, ZonedDateTime timestamp, String sql)
        {
            if ( buffer.length() > 0 ) {
                buffer.append(',');
            }
            buffer.append(sql);
        }
    }

    private final SyslogMessage msg = new SyslogMessage();
    
    @Before
    public void setup()
    {
        msg.reset();
        msg.year = (byte) 2019;
        msg.month = 8;
        msg.dayOfMonth = 18;
        msg.hour = 23;
        msg.minute = 40;
        msg.second = 18;
        msg.secondFrag = 123;
        msg.tzHours = 2;
        msg.tzMinutes = 0;

        storage = new MockStorage();
        writer = new SQLLogWriter(storage, new InMemoryHostIdManager(new Configuration()) );
    }

    @Test
    public void test1()
    {
        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|null|null|null|null|null|null", storage.buffer.toString() );
    }

    @Test
    public void test2()
    {
        msg.priority = 123;
        writer.store(msg);
        assertEquals( "123|2019-8-18 23:40:18+2:0|123|null|null|null|null|null|null", storage.buffer.toString() );
    }

    @Test
    public void test4()
    {
        msg.year = (byte) 1234;
        msg.month = 12;
        msg.dayOfMonth = 1;
        msg.hour = 2;
        msg.minute = 3;
        msg.second = 4;
        msg.secondFrag = 5;
        msg.tzHours = -6;
        msg.tzMinutes = -7;
        
        writer.store(msg);
        assertEquals( "0|1234-12-1 2:3:4-6:7|5|null|null|null|null|null|null", storage.buffer.toString() );
    }

    @Test
    public void test5()
    {
        msg.hostName = "host";
        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|1|null|null|null|null|null", storage.buffer.toString() );
    }

    @Test
    public void test6()
    {
        msg.appName = "app";
        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|null|app|null|null|null|null", storage.buffer.toString() );
    }

    @Test
    public void test7()
    {
        msg.procId = "proc";
        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|null|null|proc|null|null|null", storage.buffer.toString() );
    }

    @Test
    public void test8()
    {
        msg.msgId = "msgId";
        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|null|null|null|msgId|null|null", storage.buffer.toString() );
    }

    @Test
    public void testParams1()
    {
        final SDParam param1 = new SDParam( "id1" );
        msg.addParam(param1 );
        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|null|null|null|null|{\"data\" : [{\"id\" : id1,\"params\":null}]}|null", storage.buffer.toString() );
    }

    @Test
    public void testParams2()
    {
        final SDParam param1 = new SDParam( "id1" );
        param1.addParam( "key1", "value1" );

        msg.addParam( param1 );
        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|null|null|null|null|{\"data\" : [{\"id\" : id1,\"params\":{key1:value1}}]}|null",
                      storage.buffer.toString() );
    }

    @Test
    public void testParams3()
    {
        final SDParam param1 = new SDParam( "id1" );
        param1.addParam( "key1", "value1" );
        param1.addParam( "key2", "value2" );

        msg.addParam( param1 );
        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|null|null|null|null|{\"data\" : [{\"id\" : id1,\"params\":{key2:value2," +
                      "key1:value1}}]}|null", storage.buffer.toString() );
    }

    @Test
    public void testParams4()
    {
        final SDParam param1 = new SDParam( "id1" );
        param1.addParam( "key1", "value1" );
        param1.addParam( "key2", "value2" );

        final SDParam param2 = new SDParam( "id2" );
        param1.addParam( "key3", "value4" );

        msg.addParam(param1);
        msg.addParam(param2);

        writer.store(msg);
        assertEquals( "0|2019-8-18 23:40:18+2:0|123|null|null|null|null|{\"data\" : [{\"id\" : id2,\"params\":null},{\"id\" : id1," +
                      "\"params\":{key3:value4,key2:value2,key1:value1}}]}|null", storage.buffer.toString() );
    }
}