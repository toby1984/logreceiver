package de.codesourcery.logreceiver.ui.auth;

import de.codesourcery.logreceiver.ui.dao.IDatabaseBackend;
import de.codesourcery.logreceiver.ui.dao.User;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleAuthenticator implements IAuthenticator
{
    private static final Logger LOG = LogManager.getLogger( SimpleAuthenticator.class.getName() );

    private IDatabaseBackend dao;

    @Override
    public User authenticate(String login, String password,String httpSessionId)
    {
        Validate.notNull(login, "login must not be null");
        Validate.notNull(password, "password must not be null");
        Validate.notBlank( httpSessionId, "httpSessionId must not be null or blank");
        LOG.info("authenticate(): User '"+login+"' tries to login");
        final Optional<User> user = dao.getUserByLogin( login );
        if ( user.isEmpty() ) {
            LOG.info("authenticate(): User '"+login+"' does not exist");
            throw new RuntimeException("Authentication failed, bad login or credentials");
        }
        if ( ! user.get().activated ) {
            LOG.info("authenticate(): User '"+login+"' is not activated yet");
            throw new RuntimeException("Account is not activated yet, please check your e-mail.");
        }
        SessionListener.get().remove( user.get() );

        if ( HashUtils.comparePasswords( password, user.get().passwordHash ) ) {
            LOG.info("authenticate(): User '"+login+"' ("+user.get().id+") logged in.");
            SessionListener.get().put(httpSessionId,user.get());
            return user.get();
        }

        // TODO: Lock account after X failed attempts
        LOG.warn("authenticate(): User '"+login+"' ("+user.get().id+") provided the wrong password");
        throw new RuntimeException("Authentication failed, bad login or credentials");
    }

    @Resource
    public void setDao(IDatabaseBackend dao)
    {
        this.dao = dao;
    }
}