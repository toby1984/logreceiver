package de.codesourcery.logreceiver.ui.pages;

import de.codesourcery.logreceiver.ui.auth.LoginRequired;
import de.codesourcery.logreceiver.ui.dao.HostGroup;
import de.codesourcery.logreceiver.ui.dao.IDatabaseBackend;
import de.codesourcery.logreceiver.ui.dao.Subscription;
import de.codesourcery.logreceiver.ui.util.ClickButton;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.HeadersToolbar;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.NavigationToolbar;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.OddEvenItem;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@LoginRequired
public class ManageSubscriptionsPage extends PageWithMenu
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( ManageSubscriptionsPage.class.getName() );

    private static final int ROWS_PER_PAGE = 20;

    private WebMarkupContainer refreshContainer =
        (WebMarkupContainer) new WebMarkupContainer("refreshContainer").setOutputMarkupId(true);

    private enum MyColumns
    {
        ACTION,
        NAME,
        EXPRESSION,
        HOSTGROUP_NAME,
    }

    private enum Durations {
        NONE(null),
        MINUTES_1(Duration.ofMinutes(1)),
        MINUTES_5(Duration.ofMinutes(5)),
        MINUTES_10(Duration.ofMinutes(10)),
        MINUTES_30(Duration.ofMinutes(30)),
        MINUTES_60(Duration.ofMinutes(60)),
        HOURS_2(Duration.ofHours(2)),
        HOURS_4(Duration.ofHours(4)),
        HOURS_6(Duration.ofHours(6)),
        HOURS_12(Duration.ofHours(12)),
        HOURS_24(Duration.ofHours(24));

        private Durations(Duration d) {
            this.duration = d;
        }

        public static Durations of(Duration minutes) {
            if ( minutes == null ) {
                return Durations.NONE;
            }
            int durationMinutes = (int) minutes.toMinutes();
            Durations result = null;
            int delta = 0;
            for ( Durations d : values() )
            {
                if ( d != NONE ) {
                    int diff = Math.abs( durationMinutes - (int) d.duration.toMinutes() );
                    if ( result == null || diff < delta ) {
                        result = d;
                        delta = diff;
                    }
                }
            }
            return result;
        }

        public final Duration duration;
    }

    private final ModalWindow modalWindow = new ModalWindow("modalWindow");

    @SpringBean
    private IDatabaseBackend backend;

    final ISortableDataProvider<Subscription, MyColumns> dataProvider = new SortableDataProvider<>()
    {
        private List<Subscription> data;

        @Override
        public Iterator<? extends Subscription> iterator(long first, long count)
        {
            load();
            return data.subList((int) first, (int) (first + count)).iterator();
        }

        @Override
        public void detach()
        {
            data = null;
        }

        private void load()
        {
            if (data == null)
            {
                data = backend.getSubscriptions(currentUser());
                Comparator<Subscription> comp = (a, b) -> a.name.compareToIgnoreCase(b.name);
                boolean ascending = true;
                if (getSort() != null && getSort().getProperty() != null)
                {
                    switch (getSort().getProperty())
                    {
                        case ACTION:
                            break;
                        case NAME:
                            break;
                        case EXPRESSION:
                            comp = (a, b) -> a.expression.compareToIgnoreCase(b.expression);
                            break;
                        case HOSTGROUP_NAME:
                            comp = (a, b) -> a.hostGroup.name.compareToIgnoreCase(b.hostGroup.name);
                            break;
                    }
                    ascending = getSort().isAscending();
                }
                data.sort(ascending ? comp : comp.reversed());
            }
        }

        @Override
        public long size()
        {
            load();
            return data.size();
        }

        @Override
        public IModel<Subscription> model(Subscription object)
        {
            return Model.of(object);
        }
    };

    @Override
    protected void onInitialize()
    {
        super.onInitialize();

        final Form<Void> form = new Form<>("form");

        final DataTable<Subscription, MyColumns> dt =
            new DataTable<>("dataTable", createTableColumns(), dataProvider, ROWS_PER_PAGE) {
                @Override
                protected Item<Subscription> newRowItem(String id, int index, IModel<Subscription> model)
                {
                    return new OddEvenItem<>(id,index,model);
                }
            };
        dt.addBottomToolbar(new NavigationToolbar(dt));
        dt.addTopToolbar(new HeadersToolbar(dt, dataProvider));

        final ClickButton<Void> addButton = ClickButton.simple("addSubscription", target -> edit( new Subscription(currentUser()), target ) );

        queue( refreshContainer, form, modalWindow, dt, addButton );
    }

    private void edit(Subscription subscription, AjaxRequestTarget target)
    {
        modalWindow.setContent( createContent(modalWindow.getContentId(), subscription) );
        modalWindow.show(target);
    }

    private Component createContent(String wicketId,Subscription subscription)
    {
        final Fragment frag = new Fragment(wicketId,"addFragment",ManageSubscriptionsPage.this);

        final Form<Void> form = new Form("nestedForm");

        final FeedbackPanel feedback = new FeedbackPanel("feedback");
        feedback.setOutputMarkupPlaceholderTag(true);

        // name
        final TextField<String> name = new TextField("name", LambdaModel.of(()->subscription.name, x -> { subscription.name=x; } ) );
        name.setRequired(true);

        // expression
        final TextField<String> expression = new TextField("expression", LambdaModel.of(()->subscription.expression, x -> { subscription.expression=x; } ) );
        expression.setRequired(true);

        // host groups
        final IModel<List<HostGroup>> choices = () -> {
            final List<HostGroup> list = backend.getHostGroups(currentUser());
            list.sort( (a,b) -> a.name.compareToIgnoreCase(b.name ) );
            return list;
        };
        final IChoiceRenderer<HostGroup> renderer = new ChoiceRenderer<>() {
            @Override
            public Object getDisplayValue(HostGroup object)
            {
                return object.name;
            }
        };
        final DropDownChoice<HostGroup> groups =
            new DropDownChoice<>("hostGroups",
        LambdaModel.of( () -> subscription.hostGroup, x -> { subscription.hostGroup=x; } ), choices,renderer );
        groups.setRequired(true);

        // max. duration
        final IModel<List<Durations>> durationChoices = () -> {
            final List<Durations> list = Stream.of( Durations.values() ).collect(Collectors.toList());;
            list.sort( (a,b) -> labelFor(a).compareToIgnoreCase( labelFor(b) ) );
            return list;
        };

        final IChoiceRenderer<Durations> durationRenderer = new ChoiceRenderer<>() {
            @Override
            public Object getDisplayValue(Durations object)
            {
                return labelFor(object);
            }
        };
        final IModel<Durations> durationModel = new IModel<>() {

            @Override
            public Durations getObject()
            {
                return Durations.of( subscription.batchDuration );
            }

            @Override
            public void setObject(Durations object)
            {
                subscription.batchDuration = ((object == null) || (object == Durations.NONE)) ? null : object.duration;
            }
        };
        final DropDownChoice<Durations> batchDuration =
            new DropDownChoice<>("batchDuration",
                durationModel, durationChoices, durationRenderer);

        // max. batch size
        final IModel<String> batchSizeModel = new IModel<>() {

            @Override
            public String getObject()
            {
                return subscription.maxBatchSize == null ? null : Integer.toString(subscription.maxBatchSize);
            }

            @Override
            public void setObject(String object)
            {
                subscription.maxBatchSize = StringUtils.isBlank(object) ? null : Integer.parseInt( object.trim() );
            }
        };

        final TextField<String> maxBatchSize = new TextField<>("maxSize",batchSizeModel);
        maxBatchSize.add((IValidator<String>) validatable ->
        {
            if ( StringUtils.isNotBlank(validatable.getValue() ) )
            {
                int value=0;
                try {
                    value = Integer.parseInt( validatable.getValue().trim() );
                } catch(Exception e) {
                    validatable.error(new ValidationError("Not a valid number") );
                    return;
                }
                if ( value < 1 ) {
                    validatable.error(new ValidationError("Batch size must be >= 1"));
                }
            }
        });

        // cancel button
        final ClickButton<Void> cancel = ClickButton.simple("cancelButton", target -> modalWindow.close(target) );

        // save button
        final AjaxButton saveButton = new AjaxButton("addButton")
        {
            @Override
            protected void onError(AjaxRequestTarget target)
            {
                target.add( feedback );
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target)
            {
                try
                {
                    backend.saveSubscription(subscription );
                    refresh(target );
                    modalWindow.close(target);
                }
                catch(Exception e)
                {
                    LOG.error("onSubmit(): Failed ",e);
                    error("Something went wrong: "+e.getMessage());
                    target.add( feedback );
                }
            }
        };

        //
        frag.queue(form,name,expression,groups,batchDuration, maxBatchSize, cancel, saveButton, feedback );
        return frag;
    }

    private String labelFor(Durations d) {
        return d.name(); // TODO: Proper label...
    }

    private void refresh(AjaxRequestTarget target)
    {
        dataProvider.detach();
        target.add( refreshContainer );
    }

    private List<IColumn<Subscription,MyColumns>> createTableColumns()
    {
        final List<IColumn<Subscription,MyColumns>> result = new ArrayList<>();

        result.add(new AbstractColumn<>(Model.of("Action"))
        {
            @Override
            public void populateItem(Item<ICellPopulator<Subscription>> cellItem, String componentId, IModel<Subscription> rowModel)
            {
                final Fragment frag = new Fragment(componentId, "actionFragment", ManageSubscriptionsPage.this);

                final ClickButton<Subscription> editButton = new ClickButton<>("edit",rowModel)
                {
                    @Override
                    protected void onClick(Subscription sub,AjaxRequestTarget target)
                    {
                        edit(sub, target);
                    }
                };

                final ClickButton<Subscription> deleteButton = new ClickButton<>("delete",rowModel)
                {
                    @Override
                    protected void onClick(Subscription sub,AjaxRequestTarget target)
                    {
                        backend.deleteSubscription(sub);
                        refresh(target);
                    }
                };

                frag.queue(editButton, deleteButton);
                cellItem.add(frag);
            }
        });

        result.add(new LambdaColumn<>(Model.of("Name"), MyColumns.NAME, sub -> sub.name) );
        result.add(new LambdaColumn<>(Model.of("Expression"), MyColumns.EXPRESSION, sub -> sub.expression) );
        result.add(new LambdaColumn<>(Model.of("Host Group"), MyColumns.HOSTGROUP_NAME, sub -> sub.hostGroup.name) );

        return result;
    }
}