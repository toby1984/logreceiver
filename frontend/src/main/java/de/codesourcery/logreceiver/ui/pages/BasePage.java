package de.codesourcery.logreceiver.ui.pages;

import de.codesourcery.logreceiver.ui.MySession;
import de.codesourcery.logreceiver.ui.dao.User;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;

public class BasePage extends WebPage
{
    @Override
    public final MySession getSession()
    {
        return (MySession) super.getSession();
    }

    protected final IModel<String> resource(String key) {
        return new ResourceModel(key);
    }

    protected final User currentUser()
    {
        return getSession().getUser();
    }
}
