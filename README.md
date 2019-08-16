# logreceiver

A JDK11+ application that receives RFC 5424 syslog messages via UDP, stores them in a PostgreSQL database and provides a minimal web frontend to view/search log messages

## Building

Requires Maven 3.6.1 and JDK11.

# Running

Requires JDK11 and at least PostgreSQL 11 (as it makes use of the new partitioning features).

Just toss the generated WAR file into a servlet container of your choosing.

If you're using rsyslogd on Ubuntu (tested on Ubuntu 18.04.02), you can mirror all log messages via UDP using a configuration like this
in /etc/rsyslog.d/50-default.conf:

    *.* @localhost:1234;RSYSLOG_SyslogProtocol23Format
    
You obviously want to substitute the hostname with the host running the application.

## Features

- receive RFC 5424 syslog messages via UDP
- store log data partitioned by host and time using the new PostgreSQL partitioning feature
- automatically drop (delete) log data for each host after a certain age has been reached
- built-in web UI

## To do

- create descent web UI with some basic user management/authentication (Spring security!)
- performance improvements (less copying/GC, etc.)
- add sequence id to log message tables (one sequence per parent table)
- support running webapp without starting udp server
- track total linecount per host
- track log growth per host & hour
- support filter expressions like ( pri = 1 | pri = 2) && msg ~ 'dummy'
- support persisting filter expressions and using/recalling them
- support suscribing to filters and get an e-mail for each hit/batch of N hits (do not wait forever for N hits though,add configurable timeout to send regardless)
- track time when a filter was last hit
- track time when user last observed a filter's hit count and visually indicate whether it has been hit since then 



