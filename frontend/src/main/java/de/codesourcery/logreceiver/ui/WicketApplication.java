package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.ui.auth.LoginRequired;
import de.codesourcery.logreceiver.ui.pages.ActivateUserPage;
import de.codesourcery.logreceiver.ui.pages.HomePage;
import de.codesourcery.logreceiver.ui.pages.LoginPage;
import de.codesourcery.logreceiver.ui.pages.ManageHostGroupsPage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.time.Duration;

public class WicketApplication extends WebApplication
{
    private static final Logger LOG = LogManager.getLogger( WicketApplication.class.getName() );

    @Override
    protected void init()
    {
        super.init();
        mountPage("/home", HomePage.class);
        mountPage("/activate", ActivateUserPage.class);
        mountPage("/managehostgroups", ManageHostGroupsPage.class);

        getComponentInstantiationListeners().add(new SpringComponentInjector(this) );
        getComponentInstantiationListeners().add( component ->
        {
            final Class<? extends Component> clazz = component.getClass();
            if ( clazz.getAnnotation( LoginRequired.class ) != null )
            {
                if ( ! ((MySession) Session.get()).isUserLoggedIn() )
                {
                    LOG.info("User needs to be logged-in to access "+clazz.getName()+", redirecting");
                    throw new RestartResponseException( LoginPage.class );
                }
            }
        });

        getDebugSettings().setDevelopmentUtilitiesEnabled( true );
        getResourceSettings().setResourcePollFrequency( Duration.seconds( 1 ) );
        getDebugSettings().setAjaxDebugModeEnabled( true );
    }

    @Override
    public MySession newSession(Request request, Response response)
    {
        return new MySession( request );
    }

    @Override
    public RuntimeConfigurationType getConfigurationType()
    {
        return RuntimeConfigurationType.DEVELOPMENT;
    }

    @Override
    public Class<? extends Page> getHomePage()
    {
        return HomePage.class;
    }
}
