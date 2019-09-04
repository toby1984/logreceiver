package de.codesourcery.logreceiver.ui.dao;

import java.io.Serializable;
import java.time.Duration;

public class Subscription implements Serializable
{
    public long id;
    public User user;
    public HostGroup hostGroup;
    public String expression;
    public boolean sendAsBatch;
    public Duration batchDuration;
    public Integer maxBatchSize;
}