package de.codesourcery.logreceiver.filtering;

import de.codesourcery.logreceiver.entity.SyslogMessage;

import java.util.List;
import java.util.function.Predicate;

/**
 * Callbacks must be thread-safe.
 */
public interface IFilterCallback
{
    default void visit(List<SyslogMessage> messages) {
        messages.forEach( this::visit ); // TODO: Make all subclasses implement an optimized method for this
    }

    void visit(SyslogMessage message);

    default Predicate<SyslogMessage> getPredicate()
    {
        throw new UnsupportedOperationException( "Not implemented - getPredicate()" );
    }
}
