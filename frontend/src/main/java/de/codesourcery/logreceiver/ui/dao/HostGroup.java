package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.entity.Host;
import org.apache.commons.lang3.Validate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HostGroup implements Serializable
{
    public long id;
    public String name;
    public User user;
    public final List<Host> hosts = new ArrayList<>();

    public HostGroup() {
    }

    public HostGroup(User user)
    {
        Validate.notNull(user, "user must not be null");
        this.user = user;
    }

    public HostGroup(HostGroup other)
    {
        this.id = other.id;
        this.name = other.name;
        this.user = other.user == null ? null : other.user.copy();
        this.hosts.addAll( other.hosts.stream().map( Host::copy ).collect( Collectors.toList() ) );
    }

    public HostGroup copy()
    {
        return new HostGroup(this);
    }
}
