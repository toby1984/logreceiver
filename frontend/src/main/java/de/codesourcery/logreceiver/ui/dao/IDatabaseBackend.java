package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.entity.SyslogMessage;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface IDatabaseBackend
{
    interface IMessageVisitor extends Consumer<SyslogMessage>
    {
        void begin();

        /**
         * Invoked after the last message has been passed to this consumer.
         */
        void close(boolean success);
    }

    // users
    List<User> getAllUsers();
    Optional<User> getUserByLogin(String login);
    Optional<User> getUserById(long id);
    void saveUser(User user);
    void deleteUser(User user);
    // host group <-> user
    List<HostGroup> getHostGroups(User user);

    // host groups
    List<HostGroup> getAllHostGroups();
    Optional<HostGroup> getHostGroup(long id);
    void saveHostGroup(HostGroup group);
    void deleteHostGroup(HostGroup group);
    int getHostGroupCount();

    // subscriptions
    List<Subscription> getAllSubscriptions(boolean includeDisabled);
    List<Subscription> getSubscriptions(User user);
    void deleteSubscription(Subscription sub);

    /**
     * Remember the syslog messages matched by a given subscription.
     *
     * @param sub subscription the matches belong to
     * @param count the number of valid IDs in the ID arrays
     * @param msgIds See {@link SyslogMessage#id}
     * @param hostIds See {@link SyslogMessage#host}
     *
     * @see #visitMatchedMessages(Subscription, IMessageVisitor, int)
     */
    void storeMatchedMessages(Subscription sub,int count,long[] msgIds,long[] hostIds);

    /**
     * Visits all as-of-now unprocessed matched messages for a given subscription and deletes
     * the recorded matches afterwards.
     *
     * Matches are only deleted if the visitor does not throw any exception.
     *
     * @param sub subscription to visit messages for
     * @param visitor message visitor
     * @param maxBatchSize number of messages to process in a single batch. Larger batch size means more memory consumption.
     */
    void visitMatchedMessages(Subscription sub, IMessageVisitor visitor, int maxBatchSize);

    /**
     *
     * @param sub
     * @return <code>true</code> if a new subscription was inserted
     * or updating an existing one succeeded, <code>false</code> if an update failed
     * because the entity is no longer persistent
     */
    boolean saveSubscription(Subscription sub);
    Optional<Subscription> getSubscription(long id);

    // not part of public API
    void createTables();
}