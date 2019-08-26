package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.ui.dao.IDatabaseBackend;
import de.codesourcery.logreceiver.ui.dao.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

import java.util.Objects;
import java.util.Optional;

public class ActivateUserPage extends BasePage
{
    public static final String PARAM_USER_ID = "userId";
    public static final String PARAM_ACTIVATION_CODE = "activationCode";

    @SpringBean
    private IDatabaseBackend backend;

    private long uid;
    private String activationCode;

    public ActivateUserPage(PageParameters params)
    {
        uid = params.get(PARAM_USER_ID).toLong(0);
        activationCode  = params.get(PARAM_ACTIVATION_CODE).toString(null);
    }

    @Override
    protected void onInitialize()
    {
        super.onInitialize();

        queue( new Form<>("dummyForm") );

        final AjaxButton button = new AjaxButton("backLink")
        {
            @Override
            protected void onSubmit(AjaxRequestTarget target)
            {
                setResponsePage( WicketApplication.get().getHomePage() );
                super.onSubmit(target);
            }
        };
        button.setDefaultFormProcessing(false);
        queue(button);

        final IModel<String> msgModel = () ->
        {
            if ( uid == 0 || StringUtils.isBlank(activationCode ) )
            {
                return "Missing user ID and/or activation code";
            }
            final Optional<User> user = backend.getUserById(uid);
            if (user.isEmpty()) {
                return "Unknown user ID";
            }
            if ( user.get().activated ) {
                return "Account already activated";
            }
            if ( StringUtils.isBlank( user.get().activationCode ) ) {
                return "User has no activation code set?";
            }
            if ( ! Objects.equals(user.get().activationCode,activationCode) ) {
                return "Wrong activation code";
            }
            user.get().activated = true;
            user.get().activationCode = null;
            backend.saveUser(user.get() );
            return "User account activated.";
        };
        add( new Label("message", msgModel));
    }
}
