package de.codesourcery.logreceiver.ui.servlet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.codesourcery.logreceiver.Host;
import de.codesourcery.logreceiver.IAPI;
import de.codesourcery.logreceiver.SyslogMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RESTServlet extends HttpServlet
{
    private static final Logger LOG = LogManager.getLogger( RESTServlet.class );

    private final ObjectMapper mapper = new ObjectMapper();

    private IAPI api;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init( config );
        LOG.info("init(): REST servlet initialized.");
        api = getApplicationContext().getBean(IAPI.class);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        try
        {
            final Map<String, String[]> params = req.getParameterMap();
            LOG.info("doGet(): Params = "+toString(params) );
            final String cmd = getParamValue( "cmd", params );
            final String response;
            switch( cmd )
            {
                case "getHosts":
                    response = getAllHosts(params);
                    break;
                case "initialLoad":
                    response = initialLoad(params);
                    break;
                default:
                    throw new RuntimeException("Unknown command "+cmd);
            }
            LOG.info("doGet(): Response = \n"+response);
            resp.getWriter().print( response );
        }
        catch(Exception e)
        {
            LOG.error("doGet(): Caught ",e);
            resp.sendError( 500 , "Request lacks command" );
        }
    }

    private static String toString(Map<String, String[]> params)
    {
        final StringBuilder result = new StringBuilder();
        for (Iterator<Map.Entry<String, String[]>> iterator = params.entrySet().iterator(); iterator.hasNext(); )
        {
            Map.Entry<String, String[]> entry = iterator.next();
            String key = entry.getKey();
            String value = Stream.of( entry.getValue() ).collect( Collectors.joining( "," ) );
            result.append( key ).append( "={" ).append( value ).append( "}" );
            if ( iterator.hasNext() ) {
                result.append(" , ");
            }
        }
        return result.toString();
    }

    private String getAllHosts(Map<String,String[]> parameters) throws JsonProcessingException
    {
        return toJSON( api.getAllHosts() );
    }

    private String initialLoad(Map<String,String[]> parameters) throws JsonProcessingException
    {
        long hostId = Long.parseLong( getParamValue( "hostId", parameters ) );
        int limit = Integer.parseInt( getParamValue( "limit", parameters ) );
        final Host host = api.getHost( hostId );
        return toJSON( api.getLatestMessages( host, limit ) );
    }

    private String toJSON(Object value) throws JsonProcessingException
    {
        return mapper.writeValueAsString( value );
    }

    private String getParamValue(String param, Map<String,String[]> parameters)
    {
        final String result = getParamValue( param, null, parameters );
        if ( result == null ) {
            throw new RuntimeException("Request contains no parameter '"+param+"'");
        }
        return result;
    }

    private String getParamValue(String param, String defaultValue, Map<String,String[]> parameters) {
        final String[] value = parameters.get( param );
        if (  value == null || value.length == 0 || value[0].isBlank() ) {
            return defaultValue;
        }
        return value[0];
    }

    private Map<String,String> parseJSON(String json) throws IOException
    {
        return mapper.readValue(json, Map.class);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        resp.sendError( 403, "Forbidden" );
    }

    private WebApplicationContext getApplicationContext()
    {
        return WebApplicationContextUtils.getWebApplicationContext(getServletContext(), WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
    }
}