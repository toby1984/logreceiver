package de.codesourcery.logreceiver.ui;

import java.time.format.DateTimeFormatter;

public class HomePage extends BasePage
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( HomePage.class );

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:SS.sZ");

    @Override
    protected void onInitialize()
    {
        super.onInitialize();
    }
}