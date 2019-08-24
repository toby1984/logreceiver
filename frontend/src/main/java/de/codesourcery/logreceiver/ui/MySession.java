package de.codesourcery.logreceiver.ui;

import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

public class MySession extends WebSession
{
    public MySession(Request request)
    {
        super( request );
    }
}
