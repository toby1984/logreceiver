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

- receive RFC 5424 syslog messages via non-blocking UDP (Java NIO select())
- store log data partitioned by host and time using the new PostgreSQL partitioning feature
- automatically drop (delete) log data for each host after a certain age has been reached
- built-in web UI

## Roadmap

Items will be implemented in the order shown.

### Story: Data storage
    - backend: add sequence id to log message tables (one sequence per parent table) and make UI use it for paging instead of the timestamps  
    
### Story: support running webapp without starting udp server
         
### Story: Log filtering V1
    - UI: Add support for filtering by more than just the host
    - backend: support filtering using expressions like ( pri = 1 | pri = 2) && msg ~ 'dummy'    
    
### Story: User management
    - UI: Add/update/delete user account  
    
### Story: Session handling
    - UI: Add support for logging in/out      
    
### Story: Host management
    - UI: Add editing/deleting of existing host data (hostname,data retention time)
              
### Story: Log filtering V2
    - UI: Add support for storing/deleting/updating filters
    - backend: support persisting filter expressions and using/recalling them       
    - UI: Indicate on the UI when (and if) a filter has been last hit     
  
### Story: Filter subscriptions
    - UI: Add support for subscribing/unsubscribing to filters and receive e-mail notifications (batch or per hit)
    - backend: support suscribing to filters and get an e-mail for each hit/batch of N hits (do not wait forever for N hits though,add configurable timeout to send regardless)
    - backend: track time when a filter was last hit
    - backend: track time when user last observed a filter's hit count and visually indicate whether it has been hit since then 
        
### Story: Monitoring
    - UI: Show total log lines/grow per hour for each host
    - backend: track total linecount per host
    - backend: track log growth per host & hour
    
### Story: Performance
    - backend: Introduce multi-threadin
    - backend: less copying/GC
