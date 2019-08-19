package de.codesourcery.logreceiver.ui.servlet;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

@WebListener
public class HttpSessionInitializer implements ServletRequestListener {

    @Override public void requestDestroyed(ServletRequestEvent sre) { }

    @Override
    public void requestInitialized(ServletRequestEvent sre) {
        ((HttpServletRequest) sre.getServletRequest()).getSession();
    }
}