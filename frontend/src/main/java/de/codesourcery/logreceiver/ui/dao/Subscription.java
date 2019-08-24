package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.entity.Host;

import java.time.Duration;

public class Subscription
{
    public long id;
    public User user;
    public Host host;
    public String expression;
    public boolean sendAsBatch;
    public Duration batchDuration;

}
