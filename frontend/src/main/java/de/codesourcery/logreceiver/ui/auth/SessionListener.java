package de.codesourcery.logreceiver.ui.auth;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SessionListener implements HttpSessionListener
{
    private static final Map<String, HttpSession> sessionsById = new ConcurrentHashMap<>();

    public static final AtomicReference<SessionListener> INSTANCE = new AtomicReference<>();

    public SessionListener()  {
        INSTANCE.set(this);
    }

    public Optional<HttpSession> getSession(String id) {
        return Optional.ofNullable(sessionsById.get(id));
    }

    public static SessionListener get() {
        return INSTANCE.get();
    }

    @Override
    public void sessionCreated(HttpSessionEvent se)
    {
        sessionsById.put(se.getSession().getId(),se.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se)
    {
        sessionsById.remove(se.getSession().getId());
        final ServletContext ctx = se.getSession().getServletContext();
        final WebApplicationContext springCtx = WebApplicationContextUtils.getWebApplicationContext( ctx );
        springCtx.getBean(IAuthenticator.class).logout( se.getSession().getId() );
    }
}