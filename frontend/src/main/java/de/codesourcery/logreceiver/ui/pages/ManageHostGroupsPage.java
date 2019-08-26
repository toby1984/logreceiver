package de.codesourcery.logreceiver.ui.pages;

import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.ui.auth.LoginRequired;
import de.codesourcery.logreceiver.ui.dao.HostGroup;
import de.codesourcery.logreceiver.ui.dao.IDatabaseBackend;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
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

    protected enum Column
    {
        GROUP_NAME,
        HOSTS
    }

    @SpringBean
    private IDatabaseBackend backend;
    private ISortableDataProvider<HostGroup, Column> dataProvider = new SortableDataProvider<HostGroup, Column>()
    {
        private List<HostGroup> data;
        private Integer size;

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
        queue( createDataTable() );
    }

    private DataTable<HostGroup,Column> createDataTable()
    {
        final List<IColumn<HostGroup,Column>> columns=new ArrayList<>();
        columns.add(new LambdaColumn<>(Model.of("Name"), Column.GROUP_NAME, x -> x.name) );
        columns.add(new LambdaColumn<>(Model.of("Hosts"), Column.HOSTS, y -> y.hosts.stream().map(Host::toPrettyString).collect(Collectors.joining(","))) );
        final DefaultDataTable<HostGroup,Column> result = new DefaultDataTable<>("dataTable",columns,dataProvider,ROWS_PER_PAGE);
        return result;
    }
}
