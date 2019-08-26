package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.ui.auth.IAuthenticator;
import de.codesourcery.logreceiver.ui.dao.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.spring.injection.annot.SpringBean;

import java.util.Optional;

public class LoginPage extends BasePage
{
    private static final Logger LOG = LogManager.getLogger( LoginPage.class.getName() );

    private final FeedbackPanel feedback = new FeedbackPanel("feedback");

    @SpringBean
    private IAuthenticator authenticator;

    @Override
    protected void onInitialize()
    {
        super.onInitialize();

        if ( getSession().isUserLoggedIn() )
        {
            throw new RestartResponseException( HomePage.class );
        }

        final TextField<String> login =
                new TextField("login", Model.of("") );

        final PasswordTextField password =
                new PasswordTextField("password", Model.of("") );

        final Form<Void> form = new Form<>("loginForm") {
            @Override
            protected void onSubmit()
            {
                try
                {
                    final Optional<User> user = authenticator.authenticate( login.getModelObject(), password.getModelObject(),
                            getSession().getId() );
                    if (user.isEmpty())
                    {
                        error("Authentication failed");
                    }
                    else
                    {
                        LoginPage.this.getSession().setUser(user.get());
                        setResponsePage( HomePage.class );
                    }
                }
                catch(Exception e) {
                    LOG.error("onSubmit(): Internal error",e);
                    error("Internal error");
                }
            }
        };
        queue(form,login,password,feedback);
    }
}
