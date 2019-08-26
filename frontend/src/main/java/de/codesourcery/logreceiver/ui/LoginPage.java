package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.ui.auth.IAuthenticator;
import de.codesourcery.logreceiver.ui.dao.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;

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

        queue( new Link<Void>("registrationLink") {
            @Override
            public void onClick()
            {
                setResponsePage(RegisterUserPage.class);
            }
        });

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
                    final User user = authenticator.authenticate( login.getModelObject(), password.getModelObject(),getSession().getId() );
                    LoginPage.this.getSession().setUser(user);
                    setResponsePage( HomePage.class );
                }
                catch(Exception e) {
                    LOG.error("onSubmit(): Caught ",e);
                    error("Authentication failure: "+e.getMessage());
                }
            }
        };
        queue(form,login,password,feedback);
    }
}
