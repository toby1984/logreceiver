package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.ui.auth.IAuthenticator;
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
import java.util.function.BooleanSupplier;

public class PageWithMenu extends BasePage
{
    public static final String ITEM_WICKET_ID = "item";

    @FunctionalInterface
    interface SerializableRunnable extends Runnable, Serializable {
    }

    @FunctionalInterface
    interface SerializableBooleanSupplier extends BooleanSupplier
    {
    }

    private final class MenuItem extends Fragment
    {
        public MenuItem(String id, String linkLabel,SerializableRunnable consumer) {
            this(id,linkLabel,consumer,()->true);
        }

        public MenuItem(String id, String linkLabel,SerializableRunnable consumer,SerializableBooleanSupplier visibility) {
            this(id, Model.of(linkLabel),consumer,visibility);
        }

        public MenuItem(String id, IModel<String> linkLabel,SerializableRunnable consumer,SerializableBooleanSupplier visibility)
        {
            super(id, "menuItem", PageWithMenu.this);
            final Link<Void> link = new Link<>("link") {

                @Override
                public void onClick()
                {
                    consumer.run();
                }

                @Override
                protected void onConfigure()
                {
                    super.onConfigure();
                    setVisibilityAllowed(visibility.getAsBoolean());
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
        final MenuItem item1 = new MenuItem(ITEM_WICKET_ID,"Link1", ()-> {});
        final MenuItem item2 = new MenuItem(ITEM_WICKET_ID,"Logout", () ->
        {
            getSession().invalidate();
            setResponsePage(HomePage.class);
        }, () -> PageWithMenu.this.getSession().isUserLoggedIn() );

        final List<MenuItem> menuItems = List.of(item1, item2);

        final ListView<MenuItem> items = new ListView<>("menuItems", menuItems) {
            @Override
            protected void populateItem(ListItem<MenuItem> item)
            {
                item.add( item.getModelObject() );
            }
        };
        queue( items );
    }
}
