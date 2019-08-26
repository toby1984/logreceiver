package de.codesourcery.logreceiver.ui.pages;

import de.codesourcery.logreceiver.entity.Configuration;
import de.codesourcery.logreceiver.ui.auth.HashUtils;
import de.codesourcery.logreceiver.ui.dao.User;
import de.codesourcery.logreceiver.ui.dao.UserManager;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.validation.IFormValidator;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;
import org.apache.wicket.validation.validator.StringValidator;

import java.util.Objects;

public class RegisterUserPage extends BasePage
{
    @SpringBean
    private Configuration configuration;

    @SpringBean
    private UserManager backend;

    @Override
    protected void onInitialize()
    {
        super.onInitialize();

        final FeedbackPanel feedbackPanel = new FeedbackPanel("feedback");

        final TextField<String> login = (TextField<String>) new TextField<>("login", Model.of("") ).setRequired(true);
        login.add(StringValidator.minimumLength(1) );

        final TextField<String> email = (TextField<String>) new TextField<>("email", Model.of("") ).setRequired(true);
//        email.add( EmailAddressValidator.getInstance() );

        final PasswordTextField password = (PasswordTextField) new PasswordTextField("password", Model.of("") ).setRequired(true);
        password.add( passwordStrengthValidator() );
        final PasswordTextField passwordRepeat = (PasswordTextField) new PasswordTextField("passwordRepeat", Model.of("") ).setRequired(true);

        final Form<Void> form = new Form<>("userForm")
        {
            @Override
            protected void onSubmit()
            {
                try
                {
                    final User user = new User();
                    user.loginName = login.getModelObject();
                    user.email = email.getModelObject();
                    user.passwordHash = HashUtils.hashPassword(password.getModelObject() );
                    user.activationCode = HashUtils.generateActivationCode();
                    backend.registerUser(user);
                    setResponsePage(HomePage.class);
                }
                catch(Exception e)
                {
                    error( "User registration failed: "+e.getMessage() );
                }
            }
        };

        form.add(new IFormValidator()
        {
            @Override
            public FormComponent<?>[] getDependentFormComponents()
            {
                return new FormComponent[]{password,passwordRepeat};
            }

            @Override
            public void validate(Form<?> form)
            {
                if ( !Objects.equals( password.getModelObject(), passwordRepeat.getModelObject() ) ) {
                    form.error("Passwords do not match");
                }
            }
        });

        queue(form,login,email,password,passwordRepeat,feedbackPanel);
    }

    private IValidator<String> passwordStrengthValidator() {

        return (IValidator<String>) validatable ->
        {
            int minLength = configuration.strictPasswordPolicy ? 6 : 4;
            boolean gotDigit=false;
            boolean gotLowerCase=false;
            boolean gotUpperCase=false;
            String s = validatable.getValue();

            boolean fail=false;
            if ( s == null || s.length() < minLength ) {
                fail=true;
            }
            else if (configuration.strictPasswordPolicy)
            {
                for (int i = 0; i < s.length(); i++)
                {
                    char c = s.charAt(i);
                    gotDigit |= Character.isDigit(c);
                    gotLowerCase |= Character.isLowerCase(c);
                    gotUpperCase |= Character.isUpperCase(c);
                }
                fail |= !(gotDigit && gotUpperCase && gotLowerCase);
            }
            if ( fail )
            {
                String constraints = "";
                if ( configuration.strictPasswordPolicy ) {
                    constraints = " and must contain digits,lower-case and upper-case letters";
                }
                final ValidationError error = new ValidationError("Password needs to have at least " + minLength + " " +
                                                                      "characters"+constraints);
                validatable.error(error);
            }
        };
    }
}
