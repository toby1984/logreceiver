package de.codesourcery.logreceiver.ui.pages;

import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.storage.IHostManager;
import de.codesourcery.logreceiver.ui.auth.LoginRequired;
import de.codesourcery.logreceiver.ui.dao.HostGroup;
import de.codesourcery.logreceiver.ui.dao.IDatabaseBackend;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.spring.injection.annot.SpringBean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@LoginRequired
public class ManageHostGroupsPage extends PageWithMenu
{
    private static final int ROWS_PER_PAGE = 20;

    private final WebMarkupContainer refreshContainer =
    (WebMarkupContainer) new WebMarkupContainer( "refreshContainer" ).setOutputMarkupId( true );

    private DefaultDataTable<HostGroup, Column> dataTable;

    protected enum Column
    {
        GROUP_NAME,
        HOSTS
    }

    private final ModalWindow modal = new ModalWindow( "modalWindow" );

    @SpringBean
    private IDatabaseBackend backend;

    @SpringBean
    private IHostManager hostManager;

    private final ISortableDataProvider<HostGroup, Column> dataProvider = new SortableDataProvider<HostGroup, Column>()
    {
        private List<HostGroup> data;
        private Integer size;

        @Override
        public void detach()
        {
            data = null;
            size = null;
        }

        @Override
        public Iterator<? extends HostGroup> iterator(long first, long count)
        {
            load();
            return data.subList((int) first,(int) (first+count)).iterator();
        }

        private void load() {
            if ( data == null ) {
                data = backend.getAllHostGroups();
                data.sort( Comparator.comparing(x -> x.name ) );
            }
        }

        @Override
        public long size()
        {
            load();
            return data.size();
        }

        @Override
        public IModel<HostGroup> model(HostGroup object)
        {
            return Model.of(object);
        }
    };

    @Override
    protected void onInitialize()
    {
        super.onInitialize();

        final Form form = new Form<>("form");

        final AjaxButton addButton = new AjaxButton( "addHostGroup")
        {
            @Override
            public void onSubmit(AjaxRequestTarget target)
            {
                edit( new HostGroup(), target );
            }
        };
        addButton.setDefaultFormProcessing( false );

        this.dataTable = new DefaultDataTable<>( "dataTable", createTableColumns(), dataProvider, ROWS_PER_PAGE);
        queue( form, refreshContainer, addButton, modal,this.dataTable );
    }

    private void edit(HostGroup group, AjaxRequestTarget target)
    {
        modal.setContent( createModalContent( modal.getContentId() , group) );
        modal.show( target );
    }

    private void refresh(AjaxRequestTarget target) {
        dataProvider.detach();
        target.add( refreshContainer );
    }

    private Component createModalContent(String id,HostGroup group)
    {
        final Form form = new Form("nestedForm");
        final TextField<String> groupName = new TextField<>("groupName", new PropertyModel<>( group, "name" ) );
        groupName.setRequired( true );

        final IModel<List<Host>> choices = new LoadableDetachableModel<>()
        {
            @Override
            protected List<Host> load()
            {
                return hostManager.getAllHosts();
            }
        };
        final IModel<List<Host>> selected = new PropertyModel<>( group, "hosts" );

        final IChoiceRenderer<Host> choiceRender = new ChoiceRenderer<>() {
            @Override
            public String getDisplayValue(Host object)
            {
                return object.hostName;
            }
        };

        final Palette<Host> palette = new Palette<>( "hosts", selected, choices, choiceRender, 10, false );
        palette.add( new CustomTheme() );

        final AjaxButton cancelButton = new AjaxButton("cancelButton")
        {
            @Override
            protected void onSubmit(AjaxRequestTarget target)
            {
                modal.close( target );
            }
        };
        cancelButton.setDefaultFormProcessing( false );

        final AjaxButton addButton = new AjaxButton("addButton",form)
        {
            @Override
            protected void onSubmit(AjaxRequestTarget target)
            {
                backend.saveHostGroup( group );
                refresh( target );
                modal.close( target );
            }
        };

        final Fragment frag = new Fragment(id, "addFragment", ManageHostGroupsPage.this );
        final FeedbackPanel feedback = new FeedbackPanel( "feedback" ) {
            @Override
            public void onEvent(IEvent<?> event)
            {
                if ( event.getPayload() instanceof AjaxRequestTarget ) {
                    ((AjaxRequestTarget) event.getPayload()).add(this);
                }
            }
        };
        feedback.setOutputMarkupPlaceholderTag( true );
        frag.queue( form, cancelButton, addButton, palette, groupName, feedback );
        return frag;
    }

    private List<IColumn<HostGroup, Column>> createTableColumns()
    {
        final List<IColumn<HostGroup,Column>> columns=new ArrayList<>();
        columns.add( new AbstractColumn<>( Model.of( "Action" ) )
        {
            @Override
            public void populateItem(Item<ICellPopulator<HostGroup>> cellItem, String componentId,
                                     IModel<HostGroup> rowModel)
            {
                Fragment frag = new Fragment( componentId, "actionColumn", ManageHostGroupsPage.this );

                // edit
                final AjaxButton editButton = new AjaxButton( "editButton" )
                {
                    @Override
                    protected void onSubmit(AjaxRequestTarget target)
                    {
                        edit(rowModel.getObject(), target );
                    }
                };
                editButton.setDefaultFormProcessing( false );

                // delete
                final AjaxButton deleteButton = new AjaxButton( "deleteButton" )
                {
                    @Override
                    protected void onSubmit(AjaxRequestTarget target)
                    {
                        backend.deleteHostGroup( rowModel.getObject() );
                        refresh(target);
                    }
                };
                deleteButton.setDefaultFormProcessing( false );

                frag.queue( editButton, deleteButton );
                cellItem.add( frag );
            }
        });
        columns.add(new LambdaColumn<>(Model.of("Name"), Column.GROUP_NAME, x -> x.name) );
        columns.add(new LambdaColumn<>(Model.of("Hosts"), Column.HOSTS, y -> y.hosts.stream().map(Host::toPrettyString).collect(Collectors.joining(","))) );
        return columns;
    }

    public static final class CustomTheme extends Behavior
    {
        private static final ResourceReference CSS = new CssResourceReference( ManageHostGroupsPage.class,"palette.css");

        @Override
        public void onComponentTag(Component component, ComponentTag tag)
        {
            tag.append("class", "palette-theme-default", " ");
        }

        @Override
        public void renderHead(Component component, IHeaderResponse response)
        {
            response.render( CssHeaderItem.forReference( CSS));
        }
    }
}
