package de.codesourcery.logreceiver.ui.servlet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.codesourcery.logreceiver.IAPI;
import de.codesourcery.logreceiver.entity.Configuration;
import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.entity.SyslogMessage;
import de.codesourcery.logreceiver.filtering.IFilterCallback;
import de.codesourcery.logreceiver.formatting.PatternLogFormatter;
import de.codesourcery.logreceiver.ui.auth.IAuthenticator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ServerEndpoint(value = "/websocket/api",
    encoders = WebSocketEndpoint.ResponseEncoder.class,
    decoders = WebSocketEndpoint.APIRequestDecoder.class,
    configurator = WebSocketEndpoint.SpringContextLocator.class )
public class WebSocketEndpoint {

    private static final Logger LOG = LogManager.getLogger( WebSocketEndpoint.class);

    public static final String SERVLET_CTX_PROPERTY = "servletCtx";

    private static final ObjectMapper mapper = new ObjectMapper();

    private Session session;
    private IAPI api;
    private Configuration configuration;

    private RegExMatchingCallback callback;

    public static final class LogMessageWrapper
    {
        @JsonProperty("id")
        public final long id;

        @JsonProperty("text")
        public final String text;

        public LogMessageWrapper(long id,String text) {
            this.id = id;
            this.text = text;
        }
    }

    public static class SpringContextLocator extends ServerEndpointConfig.Configurator
    {
        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response)
        {
            try
            {
                final HttpSession httpSession = (HttpSession) request.getHttpSession();
                final ServletContext ctx = httpSession.getServletContext();
                config.getUserProperties().put( SERVLET_CTX_PROPERTY, ctx );

                final WebApplicationContext springCtx = WebApplicationContextUtils.getWebApplicationContext( ctx );
                final IAuthenticator authenticator = springCtx.getBean( IAuthenticator.class );
                if ( authenticator.getUserForSession( httpSession.getId() ).isEmpty() ) {
                    LOG.error("modifyHandshake(): Received unauthorized request for HTTP session "+httpSession.getId() );
                    throw new RuntimeException("Not authorized");
                }
            } catch(RuntimeException e) {
                throw e;
            }
        }
    }

    public enum Command
    {
        SUBSCRIBE( "subscribe", SubscribeRequest.class ),
        GET_ALL_HOSTS("get_all_hosts"),
        LAZY_LOAD("lazy_load",LazyLoadRequest.class);

        @JsonValue
        public final String jsonIdentifier;

        @JsonIgnore
        public final Class<? extends APIRequest> reqClass;

        Command(String jsonIdentifier) {
            this(jsonIdentifier, APIRequest.class);
        }

        Command(String jsonIdentifier, Class<? extends APIRequest> reqClass)
        {
            this.jsonIdentifier = jsonIdentifier;
            this.reqClass = reqClass;
        }

        public static Command fromJSONString(String value)
        {
            return Stream.of( values() ).filter( x -> x.jsonIdentifier.equals(value) ).findFirst().orElse( null );
        }
    }

    public static class APIRequest
    {
        public Command cmd;
    }

    public static final class LazyLoadRequest extends APIRequest
    {
        public int maxCount;
        public String refEntryTimestamp; // timestamp as <seconds>.<nanos>
        public long refEntryId; // DB primary key
        public boolean forwards;
    }

    public static final class SubscribeRequest extends APIRequest
    {
        public long hostId;
        public int maxCount;
        public String regex;
    }

    public static class APIResponse
    {
        public final Command cmd;
        public String responseCode="ok";
        public String errorMessage="";
        public Object payload;

        public APIResponse(Command cmd)
        {
            this.cmd = cmd;
        }
    }

    public static class LazyLoadingResponse extends APIResponse
    {
        public boolean top;

        public LazyLoadingResponse(Command cmd)
        {
            super( cmd );
        }
    }

    public static final class ResponseEncoder implements Encoder.Text<APIResponse>
    {
        @Override
        public void init(EndpointConfig ec) { }

        @Override
        public void destroy() { }

        @Override
        public String encode(APIResponse msg) throws EncodeException
        {
            try
            {
                return mapper.writeValueAsString( msg );
            }
            catch (JsonProcessingException e)
            {
                LOG.error("encode(): Encoding failed",e);
                throw new EncodeException(msg,"Encoding failed",e);
            }
        }
    }

    public static class APIRequestDecoder implements Decoder.Text<APIRequest>
    {
        @Override
        public void init(EndpointConfig ec) { }

        @Override
        public void destroy() { }

        @Override
        public boolean willDecode(String s)
        {
            return true;
        }

        @Override
        public APIRequest decode(String msg) throws DecodeException
        {
            try
            {
                final Map<String,Object> map = mapper.readValue(msg,Map.class);
                final String cmdParam = (String) map.get("cmd");
                final Command cmd = Command.fromJSONString(cmdParam);
                if ( cmd == null )
                {
                    throw new RuntimeException("Unknown command '"+cmdParam+"'");
                }
                return mapper.readValue( msg, cmd.reqClass );
            }
            catch (Exception e)
            {
                LOG.error("encode(): Decoding failed",e);
                throw new DecodeException(msg,"Decoding failed",e);
            }
        }
    }

    public WebSocketEndpoint() {
    }

    @OnOpen
    public void start(Session session)
    {
        this.session = session;
        final ServletContext ctx = (ServletContext) session.getUserProperties().get( SERVLET_CTX_PROPERTY );
        final WebApplicationContext springCtx = WebApplicationContextUtils.getWebApplicationContext( ctx );
        this.api = springCtx.getBean( IAPI.class );
        this.configuration = springCtx.getBean( Configuration.class );
        LOG.info("start(): Client "+session.getId()+" connected.");
    }

    @OnClose
    public void end()
    {
        LOG.info("end(): Client "+session.getId()+"has disconnected.");
        if ( callback != null )
        {
            callback.unsubscribe(); // sets callback to NULL as well
        }
    }

    @OnError
    public void onError(Throwable t) throws Throwable {
        LOG.error("onError(): Caught " + t.getMessage(), t);
    }

    @OnMessage
    public void handleAPIRequest(Session session, APIRequest request)
    {
        LOG.info("handleAPIRequest(): Received "+request.cmd+" from "+session.getId());
        try
        {
            final APIResponse response;
            switch( request.cmd )
            {
                case LAZY_LOAD:
                    response = new LazyLoadingResponse(Command.LAZY_LOAD);
                    LazyLoadRequest lazyReq = (LazyLoadRequest) request;
                    LOG.info("---- PAGING "+(lazyReq.forwards ? "forwards" : "backwards")+" starting at ID "+lazyReq.refEntryId+" , limit "+lazyReq.maxCount);
                    ((LazyLoadingResponse) response).top = lazyReq.forwards;

                    final IAPI.PagingDirection dir = lazyReq.forwards ? IAPI.PagingDirection.FORWARD_IN_TIME : IAPI.PagingDirection.BACKWARD_IN_TIME;
                    final List<SyslogMessage> toSend = api.getMessages( callback.host, callback, dir, lazyReq.refEntryId, lazyReq.maxCount );
                    sendToClient( toSend, response );
                    break;
                case SUBSCRIBE:
                    response = new APIResponse(Command.SUBSCRIBE);
                    SubscribeRequest req = (SubscribeRequest) request;
                    final Host host = api.getHost( req.hostId );

                    if ( callback != null )
                    {
                        callback.unsubscribe(); // sets callback to null as well
                    }

                    LOG.info("subscribe: Client sent '"+req.regex+"' with max. count "+req.maxCount);
                    final RegExMatchingCallback newCallback;
                    if ( req.regex == null || req.regex.isBlank() )
                    {
                        newCallback = new RegExMatchingCallback(host,".*")
                        {
                            private final Predicate<SyslogMessage> predicate = msg -> true;

                            @Override
                            public void visit(SyslogMessage message)
                            {
                                sendToClient(message, formatter.format( message) );
                            }

                            @Override
                            public Predicate<SyslogMessage> getPredicate()
                            {
                                return predicate;
                            }
                        };
                    } else if ( ! req.regex.contains(".*" ) && ! req.regex.contains("^") && ! req.regex.contains("$")) {
                        newCallback = new MyCallback(host, ".*"+ req.regex +".*" );
                    } else {
                        newCallback = new MyCallback(host, req.regex);
                    }
                    callback = newCallback;
                    boolean success = false;
                    final List<SyslogMessage> messages;
                    try
                    {
                        messages = api.subscribe(host, newCallback, req.maxCount);
                        success = true;
                    }
                    finally
                    {
                        if ( ! success ) {
                            callback = null;
                        }
                    }
                    sendToClient( messages,response );
                    break;
                case GET_ALL_HOSTS:
                    response = new APIResponse( Command.GET_ALL_HOSTS );
                    response.payload = api.getAllHosts();
                    session.getBasicRemote().sendObject( response );
                    break;
                default:
                    LOG.error("handleRequest(): Internal error, unhandled "+request.cmd);
                    throw new RuntimeException("Internal error, unhandled "+request.cmd);
            }
            LOG.info("handleAPIRequest(): Handled "+request.cmd+" from "+session.getId());
        }
        catch (IOException | EncodeException e)
        {
            LOG.error("handleAPIRequest(): Failed to handle request",e);
        }
    }

    private synchronized void sendToClient(List<SyslogMessage> messages, APIResponse response) throws IOException, EncodeException
    {
        // javascript expects the data to be ordered ascending by ID already
        messages.sort( Comparator.comparingLong( a -> a.id ) );
        messages.forEach( item -> LOG.info("sendToClient(): Returning #"+item.id) );
        final PatternLogFormatter formatter = PatternLogFormatter.ofPattern( configuration.defaultLogDisplayPattern );
        response.payload = messages.stream()
        .map( x -> new LogMessageWrapper(x.id,formatter.format(x)) )
        .toArray( LogMessageWrapper[]::new );
        session.getBasicRemote().sendObject( response );
    }

    public abstract class RegExMatchingCallback implements IFilterCallback
    {
        protected final Host host;
        protected final String pattern;
        protected final Pattern regex;
        protected final PatternLogFormatter formatter;

        public RegExMatchingCallback(Host host, String pattern)
        {
            this.pattern = pattern;
            this.regex = Pattern.compile(pattern,Pattern.CASE_INSENSITIVE);
            LOG.info("RegExMatchingCallback(): Matching on '"+pattern+"'");
            this.host = host;
            this.formatter = PatternLogFormatter.ofPattern( configuration.defaultLogDisplayPattern );
        }

        public final void unsubscribe()
        {
            callback = null;
            api.unsubscribe(host,this);
        }

        protected final void sendToClient(SyslogMessage message,String text)
        {
            try
            {
                final APIResponse response = new APIResponse(Command.SUBSCRIBE);
                final LogMessageWrapper wrapper = new LogMessageWrapper(message.id, text);
                response.payload = new LogMessageWrapper[]{wrapper};
                session.getBasicRemote().sendObject(response);
            }
            catch (IOException | EncodeException e)
            {
                LOG.error("Failed to send message to remote");
            }
        }
    }

    public final class MyCallback extends RegExMatchingCallback
    {
        private final Matcher matcher;
        private final Predicate<SyslogMessage> predicate;

        public MyCallback(Host host, String pattern)
        {
            super(host,pattern);
            this.matcher = regex.matcher("" );
            this.predicate = new Predicate<>()
            {
                private final Matcher localMatcher = regex.matcher("");

                @Override
                public boolean test(SyslogMessage message)
                {
                    final String text = formatter.format(message);
                    localMatcher.reset(text);
                    return localMatcher.matches();
                }
            };
        }

        @Override
        public Predicate<SyslogMessage> getPredicate()
        {
            return predicate;
        }

        @Override
        public synchronized void visit(SyslogMessage message)
        {
            final String text = formatter.format(message);
            matcher.reset(text);
            if ( matcher.matches() )
            {
                sendToClient(message,text);
            }
        }
    }
}