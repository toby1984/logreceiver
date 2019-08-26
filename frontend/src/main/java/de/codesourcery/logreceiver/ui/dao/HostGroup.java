package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.entity.Host;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class HostGroup implements Serializable
{
    public long id;
    public String name;
    public final List<Host> hosts = new ArrayList<>();
}
