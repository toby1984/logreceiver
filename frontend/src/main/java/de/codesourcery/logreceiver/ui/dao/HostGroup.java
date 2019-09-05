package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.entity.Host;
import org.apache.commons.lang3.Validate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
}
