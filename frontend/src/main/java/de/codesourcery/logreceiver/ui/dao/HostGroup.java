package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.entity.Host;

import java.util.ArrayList;
import java.util.List;

public class HostGroup
{
    public long id;
    public String name;
    public final List<Host> hosts = new ArrayList<>();
}
