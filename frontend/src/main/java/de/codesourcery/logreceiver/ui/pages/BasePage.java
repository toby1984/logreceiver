package de.codesourcery.logreceiver.ui.pages;

import de.codesourcery.logreceiver.ui.MySession;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;

public class BasePage extends WebPage
{
    @Override
    public MySession getSession()
    {
        return (MySession) super.getSession();
    }

    protected IModel<String> resource(String key) {
        return new ResourceModel(key);
    }
}
