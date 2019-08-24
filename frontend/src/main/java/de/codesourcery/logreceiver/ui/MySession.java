package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.ui.dao.User;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

public class MySession extends WebSession
{
    private User user;

    public MySession(Request request)
    {
        super( request );
    }

    public User getUser()
    {
        if ( user == null ) {
            throw new IllegalStateException( "No user logged in" );
        }
        if ( isSessionInvalidated() ) {
            throw new IllegalStateException( "Session invalidated" );
        }
        return user;
    }

    public void setUser(User user)
    {
        this.user = user;
    }

    public boolean isUserLoggedIn() {
        return ! isSessionInvalidated() && user != null;
    }
}