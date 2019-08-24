package de.codesourcery.logreceiver.ui.auth;

import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.logstorage.MessageDAO;
import de.codesourcery.logreceiver.logstorage.PostgreSQLHostIdManager;
import de.codesourcery.logreceiver.parsing.JDBCHelper;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class UserDAO implements IUserDAO
{
    private static final String USER_TABLE = "users";
    private static final String USERS_TO_HOSTGROUPS_TABLE = "users_to_host_groups";
    private static final String HOSTS_TO_HOSTGROUPS_TABLE = "hosts_to_host_groups";
    private static final String HOSTGROUPS_TABLE = "host_groups";

    private final JDBCHelper helper;

    private static final JDBCHelper.ResultSetConsumer<List<User>> USER_MAPPER = rs -> {
        List<User> result = new ArrayList<>();
        while ( rs.next() ) {
            final User user = new User();
            user.id = rs.getLong("user_id");
            user.email = rs.getString("email");
            user.loginName = rs.getString("login");
            user.passwordHash = rs.getString("password");
            result.add( user );
        }
        return result;
    };

    private final JDBCHelper.ResultSetConsumer<List<HostGroup>> GROUP_MAPPER = rs ->
    {
        final Map<Long,HostGroup> hostGroupsById = new HashMap<>();
        while ( rs.next() ) {
            final HostGroup grp = new HostGroup();
            grp.id = rs.getLong("host_group_id");
            grp.name = rs.getString("name");
            hostGroupsById.put( grp.id , grp );
        }
        if ( ! hostGroupsById.isEmpty() )
        {
            final String ids = hostGroupsById.keySet().stream().map(x->Long.toString(x)).collect( Collectors.joining(","));
            // key is host_group_id, value is host_id
            final Map<Long,Set<Long>> map = new HashMap<>();
            final JDBCHelper.ResultSetConsumer<Void> mapper2 = rs2 ->
            {
                while ( rs2.next() ) {
                    final long hostGrpId = rs2.getLong( "host_group_id" );
                    final long hostId = rs2.getLong( "host_id" );
                    final Set<Long> list = map.computeIfAbsent( hostGrpId, key -> new HashSet<>() );
                    list.add( hostId );
                }
                return null;
            };
            getHelper().execQuery( "SELECT * FROM "+HOSTS_TO_HOSTGROUPS_TABLE+" WHERE host_group_id IN ("+ids+")", mapper2 );
            final Set<Long> uniqueHostIds =
                map.values().stream().flatMap( Collection::stream ).collect( Collectors.toSet());

            final String hostIds = uniqueHostIds.stream().map(x->Long.toString(x)).collect( Collectors.joining(","));
            final String sql = "SELECT * FROM "+PostgreSQLHostIdManager.HOSTS_TABLE+" WHERE host_id IN ("+hostIds+")";
            final List<Host> hosts = getHelper().execQuery( sql, PostgreSQLHostIdManager.MAPPER );
            final Map<Long,Host> hostsById = new HashMap<>();
            for ( Host h : hosts ) {
                hostsById.put( h.id, h );
            }
            for ( var entry : map.entrySet() ) {
                final Long hostGroupId = entry.getKey();
                Set<Long> hostIds2 = entry.getValue();

                final HostGroup grp = hostGroupsById.get( hostGroupId );
                hostIds2.forEach( id -> {
                    final Host host = hostsById.get( id );
                    if ( host != null ) {
                        grp.hosts.add( host );
                    }
                });
            }
        }
        return new ArrayList<>( hostGroupsById.values() );
    };

    private JDBCHelper getHelper() {
        return helper;
    }
    public UserDAO(DataSource ds)
    {
        this.helper = new JDBCHelper(ds);
    }

    @Override
    public List<User> getAllUsers()
    {
        final String sql = "SELECT * FROM "+ USER_TABLE;
        return helper.execQuery( sql, USER_MAPPER );
    }

    @Override
    public Optional<User> getUserByLogin(String login)
    {
        return Optional.empty();
    }

    @Override
    public Optional<User> getUserById(long id)
    {
        final String sql = "SELECT * FROM "+ USER_TABLE +" WHERE user_id="+id;
        return JDBCHelper.uniqueResult( helper.execQuery( sql, USER_MAPPER ) );
    }

    @Override
    public void saveUser(User user)
    {
        if ( user.id == 0 ) {
            // new instance
            final String sql = "INSERT INTO "+ USER_TABLE +" (login,email,password) VALUES (?,?,?)";
            user.id = helper.executeInsertAutoGenKey( sql, user.loginName, user.email, user.passwordHash );
        } else {
            // update existing instance
            final String sql = "UPDATE "+ USER_TABLE +" SET login=?,email=?,password=? WHERE user_id=?";
            helper.executeUpdate( sql, user.loginName, user.email, user.passwordHash, user.id );
        }
    }

    @Override
    public void deleteUser(User user)
    {
        helper.executeUpdate("DELETE FROM "+ USER_TABLE +" WHERE user_id=?",user.id);
    }

    @Override
    public void joinHostGroup(User user, HostGroup group)
    {
        final String sql = "INSERT INTO "+USERS_TO_HOSTGROUPS_TABLE+" (host_group_id,user_id) VALUES (?,?)";
        helper.executeUpdate( sql, group.id, user.id );
    }

    @Override
    public void leaveHostGroup(User user, HostGroup group)
    {
        final String sql = "DELETE FROM "+USERS_TO_HOSTGROUPS_TABLE+" WHERE host_group_id,=? AND user_id=?";
        helper.executeUpdate( sql, group.id, user.id );
    }

    @Override
    public List<User> getMembers(HostGroup group)
    {
        final String sql = "SELECT u.* FROM "+USER_TABLE+" u, "+USERS_TO_HOSTGROUPS_TABLE+" h WHERE u.user_id=h.user_id AND h.host_group_id=?";
        return helper.execQuery( sql, USER_MAPPER, group.id );
    }

    @Override
    public List<HostGroup> getHostGroups(User user)
    {
        final String sql = "SELECT h.* FROM "+HOSTGROUPS_TABLE+" h, "+USERS_TO_HOSTGROUPS_TABLE+" u WHERE u.user_id=?";
        return helper.execQuery( sql, GROUP_MAPPER, user.id );
    }

    @Override
    public List<HostGroup> getAllHostGroups()
    {
        final String sql = "SELECT * FROM "+HOSTGROUPS_TABLE;
        return helper.execQuery( sql, GROUP_MAPPER);
    }

    @Override
    public Optional<HostGroup> getHostGroup(long id)
    {
        final String sql = "SELECT * FROM "+HOSTGROUPS_TABLE+" WHERE host_group_id=?";
        return JDBCHelper.uniqueResult(  helper.execQuery( sql, GROUP_MAPPER, id) );
    }

    @Override
    public void saveHostGroup(HostGroup group)
    {
        if ( group.id == 0 ) {
            final String sql = "INSERT INTO "+HOSTGROUPS_TABLE+" (name) VALUES (?)";
            group.id = helper.executeInsertAutoGenKey( sql,group.name );
        } else {
            final String sql = "UPDATE "+HOSTGROUPS_TABLE+" SET name=? WHERE host_group_id=?";
            helper.executeUpdate( sql,group.name, group.id );
        }
    }

    @Override
    public void deleteHostGroup(HostGroup group)
    {
        helper.executeUpdate( "DELETE FROM "+HOSTGROUPS_TABLE+" WHERE host_group_id=?",group.id );
    }

    private void createTables()
    {
        helper.doInTransaction( h ->
        {
            // users
            String sql = "CREATE SEQUENCE IF NOT EXISTS user_seq";
            h.executeUpdate( sql );

            sql = "CREATE TABLE IF NOT EXISTS "+ USER_TABLE +" (" +
                    "user_id bigint PRIMARY KEY DEFAULT nextval('user_seq'),"+
                    "login text NOT NULL,"+
                    "email text NOT NULL,"+
                    "password text NOT NULL"+
                    ")";
            h.executeUpdate( sql );

            sql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_user_login ON "+ USER_TABLE +"(lower(login))";
            h.executeUpdate( sql );

            sql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_user_email ON "+ USER_TABLE +"(lower(email))";
            h.executeUpdate( sql );

            // host groups
            sql = "CREATE SEQUENCE IF NOT EXISTS host_group_seq";
            h.executeUpdate( sql );

            sql = "CREATE TABLE IF NOT EXISTS "+ HOSTGROUPS_TABLE+" (" +
                    "host_group_id bigint PRIMARY KEY DEFAULT nextval('host_group_seq'),"+
                    "name text NOT NULL"+
                    ")";
            h.executeUpdate( sql );

            sql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_host_group_name ON "+ HOSTGROUPS_TABLE+"(lower(name))";
            h.executeUpdate( sql );

            // user-to-hostgroups
            sql = "CREATE TABLE IF NOT EXISTS "+ USERS_TO_HOSTGROUPS_TABLE+" (" +
                    "host_group_id bigint NOT NULL,"+
                    "user_id bigint NOT NULL," +
                    "FOREIGN KEY host_group_id REFERENCES "+HOSTGROUPS_TABLE+"(host_group_id),"+
                    "FOREIGN KEY user_id REFERENCES "+USER_TABLE+"(user_id)"+
                    ")";
            h.executeUpdate( sql );

            sql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_host_group ON "+ USERS_TO_HOSTGROUPS_TABLE +
                    "(host_group_id,user_id)";
            h.executeUpdate( sql );

            // hosts-to-hostgroups
            sql = "CREATE TABLE IF NOT EXISTS "+ HOSTS_TO_HOSTGROUPS_TABLE +" (" +
                    "host_group_id bigint NOT NULL,"+
                    "host_id bigint NOT NULL," +
                    "FOREIGN KEY host_group_id REFERENCES "+HOSTGROUPS_TABLE+"(host_group_id),"+
                    "FOREIGN KEY host_id REFERENCES "+ PostgreSQLHostIdManager.HOSTS_TABLE+"(host_id)"+
                    ")";
            h.executeUpdate( sql );
        });
    }

    @PostConstruct
    public void afterPropertiesSet() {
        createTables();
    }
}