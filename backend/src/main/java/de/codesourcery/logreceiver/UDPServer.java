package de.codesourcery.logreceiver;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class UDPServer
{
    private static final boolean DEBUG = true;

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( UDPServer.class.getName() );

    class PacketBuffer
    {
        public final ByteBuffer req;
        public InetAddress clientIP;

        public PacketBuffer(int bufferSize) {
            req = ByteBuffer.allocate( bufferSize );
        }

        public InputStream createInputStream(DatagramChannel chan) throws IOException
        {
            req.rewind();
            clientIP = ((InetSocketAddress) chan.receive(req)).getAddress();
            final int available = req.position();
            req.rewind();
            LOG.debug("createInputStream(): Payload size "+available);
            return new InputStream() {

                private int remaining = available;

                @Override
                public int read()
                {
                    if ( remaining == 0 ) {
                        return -1;
                    }

                    remaining--;
                    return req.get();
                }
            };
        }
    }

    private final Supplier<ILogParser> parserFactory;

    private volatile boolean shutdown;

    private final ThreadLocal<ILogParser> parser = new ThreadLocal()
    {
        @Override
        protected Object initialValue()
        {
            return parserFactory.get();
        }
    };

    private final Configuration config;

    public UDPServer(Configuration config, Supplier<ILogParser> parserFactory) {
        this.parserFactory = parserFactory;
        this.config = config;
    }

    private final AtomicReference<Selector> selector = new AtomicReference<>();

    @PostConstruct
    public void run() throws IOException, InterruptedException
    {
        if ( ! config.startUDPServer ) {
            LOG.info( "run(): =============================" );
            LOG.info( "run(): UDP server disabled in configuration." );
            LOG.info( "run(): =============================" );
            return;
        }
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicReference<IOException> result = new AtomicReference<>();
        final Thread thread = new Thread(() ->
        {
            try
            {
                started.countDown();
                process();
            }
            catch (IOException e)
            {
                result.set( e );
                LOG.error("run(): ",e);
            }
        });
        thread.start();
        if ( ! started.await(5, TimeUnit.SECONDS) ) {
            LOG.error("run(): UDP server failed to start after 5 seconds?");
        }
        Thread.sleep(1000);
        if ( result.get() != null ) {
            throw result.get();
        }
    }

    public void process() throws IOException
    {
        try ( Selector selector = Selector.open(); )
        {
            this.selector.set(selector);
            try ( DatagramChannel channel = DatagramChannel.open() )
            {
                final InetSocketAddress isa = new InetSocketAddress( config.udpPort );
                channel.socket().bind( isa );
                channel.configureBlocking( false );

                final SelectionKey clientKey = channel.register( selector, SelectionKey.OP_READ );
                clientKey.attach( new PacketBuffer( config.maxReceiveBufferSize ) );
                LOG.info( "process(): Now listening on port " + config.udpPort );
                while ( !shutdown )
                {
                    try
                    {
                        selector.select();
                        final Iterator selectedKeys = selector.selectedKeys().iterator();
                        while ( selectedKeys.hasNext() )
                        {
                            final SelectionKey key = (SelectionKey) selectedKeys.next();
                            selectedKeys.remove();

                            if ( key.isValid() && key.isReadable() )
                            {
                                read( key );
                            }
                        }
                    }
                    catch (IOException e)
                    {
                        LOG.error( "process(): Caught ", e );
                    }
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown = true;
        try
        {
            selector.get().close();
        }
        catch (IOException e)
        {
            // nothing to be done about it
        }
    }

    private void read(SelectionKey key)
    {
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("process(): Received a UDP packet on "+config.udpPort);
        }
        final DatagramChannel chan = (DatagramChannel) key.channel();
        final PacketBuffer packet = (PacketBuffer) key.attachment();
        try
        {
            final InputStream stream = packet.createInputStream( chan );
            parser.get().parse( packet.clientIP , stream );
        } catch(Exception e) {
            LOG.error("read(): Failed to parse packet",e);
        }
    }
}