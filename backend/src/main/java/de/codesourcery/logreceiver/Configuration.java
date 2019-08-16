package de.codesourcery.logreceiver;

import java.time.Duration;

public class Configuration
{
    // database
    public String dbName="test";
    public String dbUser="logreceive";
    public String dbPassword="logreceive";
    public String dbHost="localhost";
    public int dbPort = 5432;
    public int dbConnectionPoolSize = 10;

    // network
    public int udpPort = 1234;
    public int maxReceiveBufferSize = 2048; // also limits max. length of log message that can be received

    // flushing
    public int maxCharsInBuffer=100*1024;
    public Duration flushInterval = Duration.ofSeconds(10); // max. time until messages get flushed to the database

    // database layout
    public int hoursPerPartition = 4;
    public Duration defaultDataRetentionTime = Duration.ofDays( 7 );

    public Duration staleBackendUnloadCheckInterval = Duration.ofMinutes( 30 );
}
