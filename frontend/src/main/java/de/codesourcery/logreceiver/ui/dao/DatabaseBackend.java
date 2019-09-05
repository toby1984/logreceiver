package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.entity.Host;
import de.codesourcery.logreceiver.logstorage.PostgreSQLHostIdManager;
import de.codesourcery.logreceiver.parsing.JDBCHelper;
import de.codesourcery.logreceiver.ui.auth.HashUtils;
import de.codesourcery.logreceiver.util.DateUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@DependsOn(value="hostIdManager") // needed because HostIDManager#createTables() needs to run before createTables() in this method
public class DatabaseBackend implements IDatabaseBackend, ApplicationContextAware
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( DatabaseBackend.class );

    private static final String USER_TABLE = "users";
    private static final String HOSTS_TO_HOSTGROUPS_TABLE = "hosts_to_host_groups";
    private static final String HOSTGROUPS_TABLE = "host_groups";
    private static final String SUBSCRIPTIONS_TABLE = "subscriptions";

    private final ResultSetExtractor<List<Subscription>> SUBSCRIPTION_MAPPER = (rs) ->
    {
        final List<Subscription> result = new ArrayList<>();
        final Map<Long,Long> userIdsBySubscriptionId = new HashMap<>();
        final Map<Long,Long> hostGroupIdsBySubscriptionId = new HashMap<>();
        while( rs.next() )
        {
            final Subscription s = new Subscription();
            result.add( s );
            s.name = rs.getString( "name" );
            s.id = rs.getLong( "subscription_id" );
            userIdsBySubscriptionId.put( s.id, rs.getLong( "user_id" ) );
            hostGroupIdsBySubscriptionId.put( s.id, rs.getLong( "host_group_id" ) );
            s.expression = rs.getString("expression");
            Integer durationMinutes = rs.getInt( "max_batch_duration_minutes" );
            if ( ! rs.wasNull() ) {
                s.batchDuration = Duration.ofMinutes(durationMinutes);
            }
            Integer maxBatchSize = rs.getInt("max_batch_size");
            if ( ! rs.wasNull() ) {
                s.maxBatchSize = maxBatchSize;
            }
            java.sql.Timestamp watermark = rs.getTimestamp("water_mark");
            if ( watermark != null ) {
                s.watermark = watermark.toInstant().atZone( DateUtils.UTC );
            }
        }

        // fetch host groups
        final Map<Long, HostGroup> hostGroupsById = getHostGroupsById( new HashSet<>( hostGroupIdsBySubscriptionId.values() ) );
        // fetch users
        final Map<Long, User> usersById = getUsersById( new HashSet<>( userIdsBySubscriptionId.values() ) );

        for ( Subscription s : result )
        {
            final Long groupId = hostGroupIdsBySubscriptionId.get( s.id );
            s.hostGroup = hostGroupsById.get( groupId );
            s.user = usersById.get( userIdsBySubscriptionId.get( s.id ) );
        }
        return result;
    };

    private ApplicationContext applicationContext;

    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<User> USER_MAPPER = (rs,idx) -> {
        final User user = new User();
        user.id = rs.getLong("user_id");
        user.email = rs.getString("email");
        user.loginName = rs.getString("login");
        user.passwordHash = rs.getString("password");
        user.activated = rs.getBoolean("activated");
        user.activationCode = rs.getString("activation_code");
        return user;
    };

    private final ResultSetExtractor<List<HostGroup>> GROUP_MAPPER = rs ->
    {
        final Set<Long> userIds = new HashSet<>();
        final Map<Long,Long> userIdByGroupId = new HashMap<>();

        final Map<Long,HostGroup> hostGroupsById = new HashMap<>();
        while ( rs.next() ) {
            final HostGroup grp = new HostGroup();
            grp.id = rs.getLong("host_group_id");
            grp.name = rs.getString("name");
            final long userId = rs.getLong("user_id");
            userIds.add(userId);
            userIdByGroupId.put( grp.id, userId );
            hostGroupsById.put( grp.id , grp );
        }
        final Map<Long, User> users = getUsersById(userIds);
        for ( HostGroup grp : hostGroupsById.values() )
        {
            grp.user = users.get( userIdByGroupId.get( grp.id ) );
        }
        if ( ! hostGroupsById.isEmpty() )
        {
            // load all host group -> hosts mappings
            final String ids = hostGroupsById.keySet().stream().map(x->Long.toString(x)).collect( Collectors.joining(","));

            final ResultSetExtractor<Map<Long,Set<Long>>> joinTable = rs2 ->
            {
                final Map<Long,Set<Long>> map = new HashMap<>();
                while ( rs2.next() ) {
                    final long hostGrpId = rs2.getLong( "host_group_id" );
                    final long hostId = rs2.getLong( "host_id" );
                    final Set<Long> set = map.computeIfAbsent( hostGrpId, key -> new HashSet<>() );
                    set.add( hostId );
                }
                return map;
            };

            // key is host_group_id, value is all host IDs of this group
            final Map<Long, Set<Long>> hostsByGroupId = jdbcTemplate.query(
                "SELECT * FROM " + HOSTS_TO_HOSTGROUPS_TABLE + " WHERE host_group_id IN (" + ids + ")", joinTable );

            // now load all hosts for these host IDs
            final Set<Long> uniqueHostIds =
                hostsByGroupId.values().stream().flatMap( Collection::stream ).collect( Collectors.toSet() );
            final Map<Long,Host> hostsById = getHostsById( uniqueHostIds );

            for (Map.Entry<Long, Set<Long>> entry : hostsByGroupId.entrySet() )
            {
                final Long hostGroupId = entry.getKey();
                final HostGroup grp = hostGroupsById.get( hostGroupId );
                final Set<Long> hostIds2 = entry.getValue();
                hostIds2.stream().map( hostsById::get ).forEach( grp.hosts::add );
            }
        }
        return new ArrayList<>( hostGroupsById.values() );
    };

    private Map<Long,Host> getHostsById(Set<Long> ids)
    {
        if ( ids.isEmpty() ) {
            return new HashMap<>();
        }
        final String hostIds = ids.stream().map( x-> Long.toString(x) ).collect( Collectors.joining(","));
        final String sql = "SELECT * FROM "+PostgreSQLHostIdManager.HOSTS_TABLE+" WHERE host_id IN ("+hostIds+")";
        final ResultSetExtractor<Map<Long,Host>> hostMapper = resultSet ->
        {
            final Map<Long,Host> hostsById = new HashMap<>();
            while( resultSet.next() )  {
                final Host host = PostgreSQLHostIdManager.ROW_MAPPER.mapRow( resultSet,0 );
                hostsById.put( host.id, host );
            }
            return hostsById;
        };
        return jdbcTemplate.query( sql, hostMapper );
    }

    private Map<Long,HostGroup> getHostGroupsById(Set<Long> ids)
    {
        if ( ids.isEmpty() ) {
            return new HashMap<>();
        }
        final String groupIds = ids.stream().map(x->Long.toString(x)).collect(Collectors.joining( "," ));
        final String sql = "SELECT * FROM "+HOSTGROUPS_TABLE+" WHERE host_group_id IN ("+groupIds+")";
        final List<HostGroup> result = jdbcTemplate.query( sql, GROUP_MAPPER );
        final Map<Long,HostGroup> map = new HashMap<>();
        for ( HostGroup grp : result ) {
            map.put(grp.id,grp);
        }
        return map;
    }

    private Map<Long,User> getUsersById(Set<Long> ids)
    {
        if ( ids.isEmpty() ) {
            return new HashMap<>();
        }
        final String userIds = ids.stream().map( x-> Long.toString(x) ).collect( Collectors.joining(","));
        final String sql = "SELECT * FROM "+USER_TABLE+" WHERE user_id IN ("+userIds+")";
        final ResultSetExtractor<Map<Long,User>> hostMapper = resultSet ->
        {
            final Map<Long,User> usersById = new HashMap<>();
            while( resultSet.next() )
            {
                final User host = USER_MAPPER.mapRow( resultSet, 0 );
                usersById.put( host.id, host );
            }
            return usersById;
        };
        return jdbcTemplate.query( sql, hostMapper );
    }

    @Transactional
    @Override
    public List<User> getAllUsers()
    {
        return jdbcTemplate.query( "SELECT * FROM "+ USER_TABLE, USER_MAPPER );
    }

    @Transactional
    @Override
    public Optional<User> getUserByLogin(String login)
    {
        return JDBCHelper.uniqueResult(
            jdbcTemplate.query( "SELECT * FROM " + USER_TABLE + " WHERE login=?", USER_MAPPER, login ) );
    }

    @Transactional
    @Override
    public Optional<User> getUserById(long id)
    {
        final String sql = "SELECT * FROM "+ USER_TABLE +" WHERE user_id=?";
        return JDBCHelper.uniqueResult( jdbcTemplate.query( sql, USER_MAPPER, id ) );
    }

    @Transactional
    @Override
    public void saveUser(User user)
    {
        if ( user.id == 0 ) {
            // new instance
            final String sql = "INSERT INTO "+ USER_TABLE +" (login,email,password,activated,activation_code,is_admin) VALUES (?,?,?,?,?,?)";
            final GeneratedKeyHolder holder = new GeneratedKeyHolder();
            jdbcTemplate.update(con ->
            {
                final PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1,user.loginName);
                stmt.setString(2,user.email);
                stmt.setString(3,user.passwordHash);
                stmt.setBoolean(4,user.activated);
                stmt.setString(5,user.activationCode);
                stmt.setBoolean(6,user.isAdmin());
                return stmt;
            },holder);
            user.id = ((Number) holder.getKeys().get("user_id")).longValue();
        } else {
            // update existing instance
            final String sql = "UPDATE "+ USER_TABLE +" SET login=?,email=?,password=?,activated=?,activation_code=?,is_admin=? WHERE user_id=?";
            jdbcTemplate.update( sql, user.loginName, user.email, user.passwordHash, user.activated, user.activationCode, user.isAdmin(), user.id );
        }
    }

    @Transactional
    @Override
    public void deleteUser(User user)
    {
        jdbcTemplate.update("DELETE FROM "+ USER_TABLE +" WHERE user_id=?",user.id);
    }

    @Transactional
    @Override
    public List<HostGroup> getHostGroups(User user)
    {
        final String sql = "SELECT * FROM "+HOSTGROUPS_TABLE+" WHERE user_id=?";
        return jdbcTemplate.query( sql, GROUP_MAPPER, user.id );
    }

    @Override
    public int getHostGroupCount()
    {
        final String sql = "SELECT count(*) FROM "+HOSTGROUPS_TABLE;
        final int[] count = {0};
        jdbcTemplate.query(sql, resultSet ->
        {
            count[0] = resultSet.getInt(1);
        });
        return count[0];
    }

    @Transactional
    @Override
    public List<HostGroup> getAllHostGroups()
    {
        final String sql = "SELECT * FROM "+HOSTGROUPS_TABLE;
        return jdbcTemplate.query( sql, GROUP_MAPPER);
    }

    @Transactional
    @Override
    public Optional<HostGroup> getHostGroup(long id)
    {
        final String sql = "SELECT * FROM "+HOSTGROUPS_TABLE+" WHERE host_group_id=?";
        return JDBCHelper.uniqueResult( jdbcTemplate.query( sql, GROUP_MAPPER, id) );
    }

    @Transactional
    @Override
    public void saveHostGroup(HostGroup group)
    {
        if ( group.id == 0 ) {
            final String sql = "INSERT INTO "+HOSTGROUPS_TABLE+" (name,user_id) VALUES (?,?)";
            final PreparedStatementCreator cb = con -> {
                final PreparedStatement stmt = con.prepareStatement( sql, Statement.RETURN_GENERATED_KEYS );
                stmt.setString(1,group.name);
                stmt.setLong(2,group.user.id);
                return stmt;
            };
            final GeneratedKeyHolder holder = new GeneratedKeyHolder();
            jdbcTemplate.update( cb, holder );
            group.id = ((Number) holder.getKeys().get("host_group_id")).longValue();
        } else {
            final String sql = "UPDATE "+HOSTGROUPS_TABLE+" SET name=?,user_id=? WHERE host_group_id=?";
            jdbcTemplate.update( sql,group.name, group.user.id, group.id );
        }
        jdbcTemplate.update("DELETE FROM "+HOSTS_TO_HOSTGROUPS_TABLE+" WHERE host_group_id=?",group.id);
        for ( Host h : group.hosts ) {
            jdbcTemplate.update("INSERT INTO "+HOSTS_TO_HOSTGROUPS_TABLE+" (host_group_id,host_id) VALUES (?,?)", group.id, h.id );
        }
    }

    @Transactional
    @Override
    public void deleteHostGroup(HostGroup group)
    {
        jdbcTemplate.update( "DELETE FROM "+HOSTGROUPS_TABLE+" WHERE host_group_id=?",group.id );
    }

    @Transactional
    @Override
    public List<Subscription> getSubscriptions(User user)
    {
        final String sql = "SELECT * FROM "+SUBSCRIPTIONS_TABLE+" WHERE user_id=?";
        return jdbcTemplate.query(sql,SUBSCRIPTION_MAPPER,user.id);
    }

    @Transactional
    @Override
    public List<Subscription> getSubscriptions(Host host)
    {
        final String sql = "SELECT * FROM "+SUBSCRIPTIONS_TABLE+" WHERE host_id=?";
        return jdbcTemplate.query(sql,SUBSCRIPTION_MAPPER,host.id);
    }

    @Transactional
    @Override
    public void deleteSubscription(Subscription sub)
    {
        jdbcTemplate.update("DELETE FROM "+SUBSCRIPTIONS_TABLE+" WHERE subscription_id=?",sub.id);
    }

    @Transactional
    @Override
    public void saveSubscription(Subscription sub)
    {
        if ( sub.id == 0 )
        {
            /*
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS subscriptions (\n" +
                "      subscription_id bigint PRIMARY KEY DEFAULT nextval('subscriptions_seq'),\n" +
                "      name text NOT NULL,\n" +
                "      user_id bigint NOT NULL,\n" +
                "      host_group_id bigint NOT NULL,\n" +
                "      expression text NOT NULL,\n" +
                "      max_batch_duration_minutes integer,\n" +
                "      max_batch_size integer,\n" +
                "      water_mark,\n" +
                "      UNIQUE(user_id,name),\n"+
                "      FOREIGN KEY user_id REFERENCES users(user_id) ON DELETE CASCADE,\n" +
                "      FOREIGN KEY host_id REFERENCES hosts(host_id) ON DELETE CASCADE\n" +
                "    )");
             */

            final GeneratedKeyHolder holder = new GeneratedKeyHolder();
            final PreparedStatementCreator psc = con ->
            {
                final String sql = "INSERT INTO "+SUBSCRIPTIONS_TABLE+" (" +
                                       "user_id," +
                                       "name,"+
                                       "host_group_id," +
                                       "expression," +
                                       "max_batch_duration_minutes," +
                                       "max_batch_size," +
                        "water_mark) VALUES (?,?,?,?,?,?,?)";
                final PreparedStatement stmt = con.prepareStatement( sql, Statement.RETURN_GENERATED_KEYS );
                stmt.setLong( 1, sub.user.id );
                stmt.setString( 2, sub.name );
                stmt.setLong( 3, sub.hostGroup.id );
                stmt.setString( 4, sub.expression);
                if ( sub.batchDuration == null ) {
                    stmt.setNull( 5, Types.INTEGER );
                }
                else
                {
                    stmt.setObject( 5, sub.batchDuration.toMinutes() );
                }
                if ( sub.maxBatchSize == null ) {
                    stmt.setNull( 6, Types.INTEGER );
                }
                else
                {
                    stmt.setObject( 6, sub.maxBatchSize );
                }
                if ( sub.watermark == null ) {
                    stmt.setNull( 7, Types.TIMESTAMP);
                }
                else
                {
                    stmt.setObject( 7, Timestamp.valueOf( sub.watermark.toLocalDateTime() ) );
                }
                return stmt;
            };
            jdbcTemplate.update(psc, holder );
            sub.id = ((Number) holder.getKeys().get("subscription_id")).longValue();
        } else {
            jdbcTemplate.update("UPDATE "+SUBSCRIPTIONS_TABLE+" SET user_id=?,name=?," +
                                    "host_group_id=?,expression=?," +
                            "max_batch_duration_minutes=?,max_batch_size=?,water_mark=? WHERE" +
                                    " subscription_id=?",
                sub.user.id,
                sub.name,
                sub.hostGroup.id,
                sub.expression,
                sub.batchDuration == null ? null : sub.batchDuration.toMinutes(),
                sub.maxBatchSize,
                sub.watermark == null ? null : java.sql.Timestamp.valueOf(sub.watermark.toLocalDateTime()),
                sub.id);
        }
    }

    /**
     * Not part of public API.
     */
    @Transactional
    public void createTables()
    {
        // users
        final String[] sql = { "        CREATE SEQUENCE IF NOT EXISTS user_seq",
                               "        CREATE TABLE IF NOT EXISTS users (\n" +
                               "                user_id bigint PRIMARY KEY DEFAULT nextval('user_seq'),\n" +
                               "                login text NOT NULL,\n" +
                               "                email text NOT NULL,\n" +
                               "                activation_code text DEFAULT NULL,\n" +
                               "                activated boolean NOT NULL DEFAULT false,\n" +
                               "                password text NOT NULL," +
                               "                is_admin boolean NOT NULL)",
                               "        CREATE UNIQUE INDEX IF NOT EXISTS idx_user_login ON users(lower(login))",
                               "        CREATE UNIQUE INDEX IF NOT EXISTS idx_user_email ON users(lower(email))",
                               "        CREATE SEQUENCE IF NOT EXISTS host_group_seq",
                               "        CREATE TABLE IF NOT EXISTS host_groups (\n" +
                               "                host_group_id bigint PRIMARY KEY DEFAULT nextval('host_group_seq'),\n" +
                               "                user_id bigint NOT NULL,\n"+
                               "                name text NOT NULL," +
                               "                FOREIGN KEY (user_id) REFERENCES users(user_id)"+
                               ")",
                               "CREATE UNIQUE INDEX IF NOT EXISTS idx_host_group_name  ON host_groups(user_id,lower(name))",
                               "        CREATE UNIQUE INDEX IF NOT EXISTS idx_host_group_name ON host_groups(lower(name))",
                               "        CREATE TABLE IF NOT EXISTS hosts_to_host_groups (\n" +
                               "                host_group_id bigint NOT NULL,\n" +
                               "                host_id bigint NOT NULL,\n" +
                               "                FOREIGN KEY (host_group_id) REFERENCES host_groups(host_group_id) ON DELETE CASCADE,\n" +
                               "                FOREIGN KEY (host_id) REFERENCES log_hosts(host_id) ON DELETE CASCADE," +
                               "                PRIMARY KEY(host_group_id,host_id)\n" +
                               "                )",
                               "        CREATE SEQUENCE IF NOT EXISTS subscriptions_seq",
                               "        CREATE TABLE IF NOT EXISTS subscriptions (\n" +
                               "                      subscription_id bigint PRIMARY KEY DEFAULT nextval('subscriptions_seq'),\n"+
                               "                      name text NOT NULL,\n"+
                               "                      user_id bigint NOT NULL,\n" +
                               "                      host_group_id bigint NOT NULL,\n" +
                               "                      expression text NOT NULL,\n" +
                               "                      max_batch_duration_minutes integer,\n" +
                               "                      max_batch_size integer,\n" +
                               "                      water_mark timestamptz,\n" +
                               "                      FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,\n" +
                               "                      FOREIGN KEY (host_group_id) REFERENCES host_groups(host_group_id) ON DELETE CASCADE\n" +
                               "                    )",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_name  ON subscriptions(user_id,lower(name))"};
        for (String s : sql)
        {
            LOG.info("executing \n"+s);
            jdbcTemplate.update( s );
        }

        if ( getUserByLogin( "admin" ).isEmpty() ) {
            final User admin=new User();
            admin.email="root@localhost";
            admin.loginName="admin";
            admin.passwordHash= HashUtils.hashPassword("admin");
            admin.activated = true;
            saveUser( admin );
        }
    }

    @PostConstruct
    public void afterPropertiesSet() {
        getSpringProxy().createTables();
    }

    private IDatabaseBackend getSpringProxy() {
        return this.applicationContext.getBean( IDatabaseBackend.class );
    }

    @Resource
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }
}