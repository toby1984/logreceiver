package de.codesourcery.logreceiver.ui;

import org.apache.wicket.Page;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.time.Duration;

public class WicketApplication extends WebApplication
{
    @Override
    protected void init()
    {
        super.init();
        mountPage("/home",HomePage.class);
        getComponentInstantiationListeners().add(new SpringComponentInjector(this) );

        getDebugSettings().setDevelopmentUtilitiesEnabled( true );
        getResourceSettings().setResourcePollFrequency( Duration.seconds( 1 ) );
        getDebugSettings().setAjaxDebugModeEnabled( true );
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
