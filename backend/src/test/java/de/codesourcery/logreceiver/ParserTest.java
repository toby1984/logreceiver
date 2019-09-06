package de.codesourcery.logreceiver;

import de.codesourcery.logreceiver.entity.Configuration;
import de.codesourcery.logreceiver.parsing.ILogParser;
import de.codesourcery.logreceiver.parsing.RFC5424Parser;
import de.codesourcery.logreceiver.logstorage.SQLLogWriter;
import de.codesourcery.logreceiver.storage.InMemoryHostIdManager;
import de.codesourcery.logreceiver.util.EventBus;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class ParserTest
{
    private SQLLogWriterTest.MockStorage storage;

    @Before
    public void setup() {
        storage = new SQLLogWriterTest.MockStorage();
    }

    @Test
    public void testParseMessageWithoutPayload() throws IOException
    {
        final InputStream in = getClass().getResourceAsStream( "/example.txt" );
        final BufferedReader reader = new BufferedReader( new InputStreamReader( in ) );
        List<String> lines = new ArrayList<>();
        String line;
        while ( ( line = reader.readLine() ) != null ) {
            lines.add( line );
        }

        final InMemoryHostIdManager hostIdManager =
                new InMemoryHostIdManager( new Configuration(), new EventBus() );
        final SQLLogWriter writer = new SQLLogWriter(storage, hostIdManager );
        final ILogParser p = new RFC5424Parser( hostIdManager, writer );

        final InetAddress localhost = InetAddress.getLocalHost();
        for ( String s : lines )
        {
            p.parse( localhost, new InputStream() {

                private char[] data = s.toCharArray();
                private int ptr = 0;

                @Override
                public int read() throws IOException
                {
                    if ( ptr >= data.length ) {
                        return -1;
                    }
                    return data[ptr++];
                }
            });
        }
        System.out.println("BUFFER: "+storage.buffer);
    }
}
