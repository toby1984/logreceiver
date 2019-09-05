package de.codesourcery.logreceiver.ui.dao;

import java.io.Serializable;
import java.time.Duration;
import java.time.ZonedDateTime;

public class Subscription implements Serializable
{
    public long id;
    public User user;
    public String name;
    public HostGroup hostGroup;
    public String expression;
    public Duration batchDuration;
    public Integer maxBatchSize;
    public ZonedDateTime watermark;

    public Subscription()
    {
    }

    public Subscription(User user)
    {
        this.user = user;
    }
}