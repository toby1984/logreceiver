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

    public User() {
    }

    public User(User other) {
        this.id = other.id;
        this.loginName =other.loginName;
        this.email = other.email;
        this.passwordHash = other.passwordHash;
        this.activationCode = other.activationCode;
        this.activated = other.activated;
        this.isAdmin = other.isAdmin;
    }

    public User copy() {
        return new User(this);
    }

    public boolean isAdmin()
    {
        return isAdmin;
    }

    public void setAdmin(boolean isAdmin)
    {
        this.isAdmin = isAdmin;
    }
}
