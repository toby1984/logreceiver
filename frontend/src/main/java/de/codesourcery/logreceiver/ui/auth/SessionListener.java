package de.codesourcery.logreceiver.ui.auth;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

public class SessionListener implements HttpSessionListener
{
    @Override
    public void sessionCreated(HttpSessionEvent se)
    {
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se)
    {
        final ServletContext ctx = se.getSession().getServletContext();
        final WebApplicationContext springCtx = WebApplicationContextUtils.getWebApplicationContext( ctx );
        springCtx.getBean(IAuthenticator.class).logout( se.getSession().getId() );
    }
}