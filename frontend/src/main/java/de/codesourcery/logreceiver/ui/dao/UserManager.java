package de.codesourcery.logreceiver.ui.dao;

import de.codesourcery.logreceiver.ui.EmailHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
public class UserManager
{
    private IDatabaseBackend backend;
    private EmailHelper emailHelper;

    @Transactional
    public void registerUser(User user)
    {
        backend.saveUser(user);
        emailHelper.sendRegistrationEmail(user.email, user.id, user.activationCode);
    }

    @Resource
    public void setBackend(IDatabaseBackend backend)
    {
        this.backend = backend;
    }

    @Resource
    public void setEmailHelper(EmailHelper emailHelper)
    {
        this.emailHelper = emailHelper;
    }
}
