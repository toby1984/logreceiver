package de.codesourcery.logreceiver.ui.dao;

import java.io.Serializable;

public class User implements Serializable
{
    public long id;
    public String loginName;
    public String email;
    public String passwordHash;
    public String activationCode;
    public boolean activated;
    private boolean isAdmin;

    public boolean isAdmin()
    {
        return isAdmin;
    }

    public void setAdmin(boolean isAdmin)
    {
        this.isAdmin = isAdmin;
    }
}
