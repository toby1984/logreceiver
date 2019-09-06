package de.codesourcery.logreceiver.ui.servlet;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class IntelliJFilter implements Filter
{
    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {

    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException
    {
        final HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        final String agent = request.getHeader( "user-agent" );
        if ( agent == null || !agent.toLowerCase().contains( "intelli" ) )
        {
            chain.doFilter( req, resp );
        } else {
            final String destinationPath = request.getRequestURL().toString();
            response.setStatus( 200 );
            response.setContentType( "text/html" );
            final ServletOutputStream out = response.getOutputStream();
            final String html = " <html xmlns=\"http://www.w3.org/1999/xhtml\">    \n" +
                    "  <head>      \n" +
                    "    <title>IntellIJ Redirect</title>      \n" +
                    "    <meta http-equiv=\"refresh\" content=\"0;URL='"+destinationPath+"'\" />    \n" +
                    "  </head>    \n" +
                    "  <body> \n" +
                    "    <p>This page has moved to a <a href=\""+destinationPath+"\">\n" +
                    "      "+destinationPath+"</a>.</p> \n" +
                    "  </body>  \n" +
                    "</html>";
            out.print(html);
            out.flush();
        }
    }

    @Override
    public void destroy()
    {

    }
}
