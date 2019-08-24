package de.codesourcery.logreceiver.ui.auth;

import java.util.List;
import java.util.Optional;

public interface IUserDAO
{
    List<User> getAllUsers();
    Optional<User> getUserByLogin(String login);
    Optional<User> getUserById(long id);
    void saveUser(User user);
    void deleteUser(User user);
    //
    void joinHostGroup(User user, HostGroup group);
    void leaveHostGroup(User user, HostGroup group);
    List<User> getMembers(HostGroup group);
    List<HostGroup> getHostGroups(User user);
    // --
    List<HostGroup> getAllHostGroups();
    Optional<HostGroup> getHostGroup(long id);
    void saveHostGroup(HostGroup group);
    void deleteHostGroup(HostGroup group);
}
