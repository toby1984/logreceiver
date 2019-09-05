package de.codesourcery.logreceiver.ui.util;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.model.IModel;
import org.danekja.java.util.function.serializable.SerializableConsumer;
import org.danekja.java.util.function.serializable.SerializableSupplier;

public abstract class ClickButton<T> extends AjaxButton
{
    private IModel<T> model;

    public ClickButton(String wicketId) {
        super(wicketId);
        setDefaultFormProcessing(false);
    }

    public ClickButton(String wicketId,IModel<T> model) {
        super(wicketId);
        this.model = model;
        setDefaultFormProcessing(false);
    }

    public ClickButton(String wicketId, SerializableSupplier<T> supplier) {
        super(wicketId);
        this.model = supplier::get;
        setDefaultFormProcessing(false);
    }

    public static ClickButton<Void> simple(String wicketId, SerializableConsumer<AjaxRequestTarget> func)
    {
        return new ClickButton<>( wicketId ) {

            @Override
            protected void onClick(Void data, AjaxRequestTarget target)
            {
                func.accept(target );
            }
        };
    }

    @Override
    protected final void onSubmit(AjaxRequestTarget target)
    {
        onClick( model == null ? null : model.getObject(), target );
    }

    protected abstract void onClick(T data,AjaxRequestTarget target);
}
