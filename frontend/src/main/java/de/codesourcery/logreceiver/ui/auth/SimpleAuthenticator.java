package de.codesourcery.logreceiver.ui.auth;

import de.codesourcery.logreceiver.ui.dao.IDatabaseBackend;
import de.codesourcery.logreceiver.ui.dao.User;
import jdk.jshell.spi.ExecutionControl;
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
    public Optional<User> authenticate(String login, String password,String httpSessionId)
    {
        Validate.notNull(login, "login must not be null");
        Validate.notNull(password, "password must not be null");
        Validate.notBlank( httpSessionId, "httpSessionId must not be null or blank");
        LOG.info("authenticate(): User '"+login+"' tries to login");
        final Optional<User> user = dao.getUserByLogin( login );
        if ( user.isEmpty() ) {
            LOG.warn("authenticate(): User '"+login+"' does not exist");
            return Optional.empty();
        }
        SessionListener.get().remove( user.get() );

        if ( HashUtils.comparePasswords( password, user.get().passwordHash ) ) {
            LOG.info("authenticate(): User '"+login+"' ("+user.get().id+") logged in.");
            SessionListener.get().put(httpSessionId,user.get());
            return user;
        }
        // TODO: Lock account after X failed attempts
        LOG.warn("authenticate(): User '"+login+"' ("+user.get().id+") provided the wrong password");
        return Optional.empty();
    }

    @Resource
    public void setDao(IDatabaseBackend dao)
    {
        this.dao = dao;
    }
}