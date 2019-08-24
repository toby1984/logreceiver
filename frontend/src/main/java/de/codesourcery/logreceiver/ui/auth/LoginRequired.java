package de.codesourcery.logreceiver.ui.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Documented
@Retention( value = RetentionPolicy.RUNTIME )
@Inherited
public @interface LoginRequired
{
}