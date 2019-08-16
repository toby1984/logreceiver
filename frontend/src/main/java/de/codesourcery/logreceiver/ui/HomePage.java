package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.Host;
import de.codesourcery.logreceiver.IAPI;
import de.codesourcery.logreceiver.SyslogMessage;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.list.OddEvenListItem;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class HomePage extends BasePage
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( HomePage.class );

    private static final int MAX_ROWS = 5;

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:SS.sZ");

    private Host selectedHost;

    @SpringBean(name="api")
    private IAPI api;

    private ZonedDateTime refDate=ZonedDateTime.now();
    private boolean ascending=false;
    private boolean moreAvailable;

    private ListView<SyslogMessage> repeater;

    private final IModel<List<SyslogMessage>> provider = new LoadableDetachableModel<>()
    {
        @Override
        public List<SyslogMessage> load()
        {
            if ( selectedHost == null ) {
                return new ArrayList<>();
            }

            LOG.info("load(): Getting data for "+selectedHost+" @ "+refDate+" "+(ascending?"asc":"desc"));
            List<SyslogMessage> rows = api.getMessages(selectedHost, refDate, ascending, MAX_ROWS + 1);
            if ( rows.size() > MAX_ROWS ) {
                rows = rows.subList(0, MAX_ROWS );
                moreAvailable = true;
            } else {
                moreAvailable = false;
            }
            return rows;
        }
    };

    @Override
    protected void onInitialize()
    {
        super.onInitialize();

        final WebMarkupContainer repeaterContainer = new WebMarkupContainer("container");
        repeaterContainer.setRenderBodyOnly(false);
        repeaterContainer.setOutputMarkupPlaceholderTag(true);

        final IModel<List<Host>> choiceModel = () -> api.getAllHosts().stream().sorted(Comparator.comparing(a -> a.hostName)).collect(Collectors.toList());
        final IChoiceRenderer<Host> renderer = new ChoiceRenderer<>() {
            @Override
            public Object getDisplayValue(Host object)
            {
                if ( object.hostName != null ) {
                    return object.hostName;
                }
                return object.ip.toString();
            }

            @Override
            public String getIdValue(Host object, int index)
            {
                return Integer.toString(index);
            }
        };

        final List<Host> choices = choiceModel.getObject();
        if ( ! choices.isEmpty() ) {
            selectedHost = choices.get(0);
        }

        final DropDownChoice<Host> choice = new DropDownChoice<>("hostSelect",new PropertyModel<>(this,"selectedHost"),choiceModel,renderer);

        // links
        final AjaxLink<Void> older = new AjaxLink<>("previous") {

            @Override
            public void onClick(AjaxRequestTarget target)
            {
                final List<SyslogMessage> msgs = provider.getObject();
                if ( ! msgs.isEmpty() )
                {
                    ascending = false;
                    refDate = msgs.get(0).getTimestamp();
                    provider.detach();
                    target.add(repeaterContainer);
                }
            }

            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                setEnabled(!ascending && moreAvailable );
            }
        };
        older.setOutputMarkupPlaceholderTag(true);

        final AjaxLink<Void> newer = new AjaxLink<>("next") {

            @Override
            public void onClick(AjaxRequestTarget target)
            {
                final List<SyslogMessage> msgs = provider.getObject();
                if ( ! msgs.isEmpty() )
                {
                    refDate = msgs.get( msgs.size()-1 ).getTimestamp();
                    ascending = true;
                    provider.detach();
                    target.add(repeaterContainer);
                }
            }

            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                setEnabled(ascending && moreAvailable );
            }
        };
        newer.setOutputMarkupPlaceholderTag(true);

        // table
        repeater = new ListView<>("dataTable",provider)
        {
            @Override
            protected ListItem<SyslogMessage> newItem(int index, IModel<SyslogMessage> itemModel)
            {
                return new OddEvenListItem<>(index, itemModel);
            }

            @Override
            protected void populateItem(ListItem<SyslogMessage> item)
            {
                item.add( new Label("timestamp", () -> FORMAT.format(item.getModelObject().getTimestamp())));
                item.add( new Label("prio", () -> item.getModelObject().priority));
                item.add( new Label("message", () -> item.getModelObject().message));
            }
        };

        // form
        final Form form = new Form<>("form");

        queue(form,choice,repeater,older,newer,repeaterContainer);
    }
}
