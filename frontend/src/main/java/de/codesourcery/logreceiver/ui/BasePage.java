package de.codesourcery.logreceiver.ui;

import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;

public class BasePage extends WebPage
{
    @Override
    public MySession getSession()
    {
        return (MySession) super.getSession();
    }
}
