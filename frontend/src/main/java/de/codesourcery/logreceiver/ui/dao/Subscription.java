package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.formatting.PatternLogFormatter;

import java.io.Serializable;
import java.time.Duration;
import java.time.ZonedDateTime;

public class Subscription implements Serializable
{
    public long id;
    public boolean enabled=true;
    public User user;
    public String name;
    public HostGroup hostGroup;
    public String expression;
    public Duration batchDuration;
    public Integer maxBatchSize;
    public ZonedDateTime watermark;
    public String logPattern = PatternLogFormatter.DEFAULT_PATTERN;

    public Subscription()
    {
    }

    public Subscription(User user)
    {
        this.user = user;
    }

    public Subscription(Subscription other) {
        this.id = other.id;
        this.enabled = other.enabled;
        this.user = other.user == null ? null : other.user.copy();
        this.name = other.name;
        this.hostGroup = other.hostGroup == null ? null : other.hostGroup.copy();
        this.expression = other.expression;
        this.batchDuration = other.batchDuration;
        this.maxBatchSize = other.maxBatchSize;
        this.watermark = other.watermark;
        this.logPattern = other.logPattern;
    }

    public Subscription copy() {
        return new Subscription( this );
    }

    public boolean isBatchDurationExceeded(ZonedDateTime now)
    {
        if ( batchDuration == null ) {
            return false;
        }
        if ( watermark == null ) {
            watermark = now;
            return false;
        }
        final long seconds = now.toEpochSecond() - watermark.toEpochSecond();
        return seconds >= batchDuration.toSeconds();
    }
}