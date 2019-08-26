package de.codesourcery.logreceiver.ui.auth;

import de.codesourcery.logreceiver.ui.dao.User;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SessionListener implements HttpSessionListener
{
    private final Map<String, User> userBySessionID = new ConcurrentHashMap<>();
    private final Map<String, HttpSession> sessionsById = new ConcurrentHashMap<>();

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

    public void remove(User user)
    {
        final Set<String> sessionIds=new HashSet<>();
        for (Iterator<Map.Entry<String, User>> iterator = userBySessionID.entrySet().iterator(); iterator.hasNext(); )
        {
            Map.Entry<String, User> entry = iterator.next();
            if ( entry.getValue().id == user.id ) {
                sessionIds.add(entry.getKey());
                iterator.remove();
            }
        }

        sessionIds.forEach(sessionId ->
        {
            final HttpSession session = sessionsById.remove(sessionId);
            if ( session != null )
            {
                try
                {
                    session.invalidate();
                } catch(IllegalStateException e) {
                    // ok
                }
            }
        });
    }

    public void put(String sessionId,User user) {
        userBySessionID.put(sessionId,user);
    }

    @Override
    public void sessionCreated(HttpSessionEvent se)
    {
        sessionsById.put(se.getSession().getId(),se.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se)
    {
        final String sessionId = se.getSession().getId();
        userBySessionID.remove(sessionId);
        sessionsById.remove(sessionId);
    }

    public User getUser(String sessionId)
    {
        return userBySessionID.get(sessionId);
    }
}