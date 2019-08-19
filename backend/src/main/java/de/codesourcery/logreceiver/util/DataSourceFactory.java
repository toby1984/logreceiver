package de.codesourcery.logreceiver.util;

import de.codesourcery.logreceiver.entity.Configuration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.springframework.beans.factory.FactoryBean;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

public class DataSourceFactory implements FactoryBean<DataSource>
{
    private final Configuration config;

    private DataSource instance;

    public DataSourceFactory(Configuration config) {
        this.config = config;
    }

    @Override
    public synchronized DataSource getObject() throws Exception
    {
        if ( instance == null ) {
            instance = createDatasource();
        }
        return instance;
    }

    private DataSource createDatasource() throws Exception
    {
        final int poolSize = config.dbConnectionPoolSize;
        final String url = "jdbc:postgresql://"+config.dbHost+":"+config.dbPort+"/"+config.dbName;
        final PoolProperties p = new PoolProperties();

        final String jdbcUrl = url.trim();
        p.setUrl( jdbcUrl );
        p.setDriverClassName("org.postgresql.Driver");
        p.setUsername( config.dbUser );
        p.setPassword( config.dbPassword );

        p.setJmxEnabled(false);

        p.setTestWhileIdle(false);
        p.setTestOnBorrow(true);
        p.setValidationQuery("SELECT 1");
        p.setTestOnReturn(false);
        p.setValidationInterval(30000);
        p.setMaxAge( Duration.ofHours( 1 ).toMillis() );
        p.setTimeBetweenEvictionRunsMillis( (int) Duration.ofHours( 1 ).toMillis() );
        p.setMinEvictableIdleTimeMillis( Integer.MAX_VALUE );

        p.setInitialSize(poolSize);
        p.setMaxActive(poolSize);

        p.setMinIdle(poolSize);
        p.setMaxIdle(poolSize);

        final List<String> interceptors = List.of(
            "org.apache.tomcat.jdbc.pool.interceptor.ConnectionState",
            "org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer" );

        final String interceptorString = String.join( ";", interceptors );
        p.setJdbcInterceptors( interceptorString );

        final org.apache.tomcat.jdbc.pool.DataSource datasource = new org.apache.tomcat.jdbc.pool.DataSource();
        datasource.setPoolProperties(p);
        return datasource;
    }

    @Override
    public Class<?> getObjectType()
    {
        return DataSource.class;
    }

    @Override
    public boolean isSingleton()
    {
        return true;
    }
}
