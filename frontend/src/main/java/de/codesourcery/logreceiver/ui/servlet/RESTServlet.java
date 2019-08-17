package de.codesourcery.logreceiver.ui.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

public class RESTServlet extends HttpServlet
{
    private static final Logger LOG = LogManager.getLogger( RESTServlet.class );

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init( config );
        LOG.info("init(): REST servlet initialized.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        final String uri = req.getRequestURI();
        req.getParameterMap();
        req.getContextPath();
        LOG.info("doGet(): Called with "+uri);
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