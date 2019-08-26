package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.ui.auth.IAuthenticator;
import de.codesourcery.logreceiver.ui.dao.User;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;

import java.io.Serializable;
import java.util.List;
import java.util.function.Consumer;

public class PageWithMenu extends BasePage
{
    interface SerializableConsumer<T> extends Consumer<T>, Serializable {
    }

    private class MenuItem<T> extends Fragment
    {
        public MenuItem(String id, String linkLabel,SerializableConsumer<T> consumer) {
            this(id, Model.of(linkLabel),consumer);
        }

        public MenuItem(String id, IModel<String> linkLabel,SerializableConsumer<T> consumer)
        {
            super(id, "menuItem", PageWithMenu.this);
            final Link<T> link = new Link<>("link") {

                @Override
                public void onClick()
                {
                    consumer.accept(getModelObject());
                }
            };
            final Label label = new Label("label",linkLabel);
            queue(link,label);
        }
    }

    @SpringBean
    private IAuthenticator authenticator;

    @Override
    protected void onInitialize()
    {
        super.onInitialize();
        final MenuItem item1 = new MenuItem("item","Link1", x -> {});
        final MenuItem item2 = new MenuItem("item","Logout", x ->
        {
            getSession().invalidate();
            setResponsePage(HomePage.class);
        })
        {
            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                setVisible( PageWithMenu.this.getSession().isUserLoggedIn() );
            }
        };

        final ListView<MenuItem> items = new ListView<>("menuItems", List.of(item1,item2)) {
            @Override
            protected void populateItem(ListItem<MenuItem> item)
            {
                item.add( item.getModelObject() );
            }
        };
        queue( items );
    }
}
