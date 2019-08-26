package de.codesourcery.logreceiver.ui;

import de.codesourcery.logreceiver.entity.Configuration;
import org.apache.commons.lang3.Validate;
import org.apache.wicket.Page;
import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

import static javax.mail.Message.RecipientType;

@Component
public class EmailHelper
{
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger( EmailHelper.class.getName() );

    private Configuration configuration;

    public void sendRegistrationEmail(String recipient,long userId,String activationCode) {
        Validate.notBlank( recipient, "recipient must not be null or blank");
        Validate.notBlank( activationCode, "activationCode must not be null or blank");

        final PageParameters pageParameters = new PageParameters();
        pageParameters.add(ActivateUserPage.PARAM_USER_ID, Long.toString(userId));
        pageParameters.add(ActivateUserPage.PARAM_ACTIVATION_CODE, activationCode);

        final String fullUrl  = getAbsoluteUrl(ActivateUserPage.class, pageParameters );

        final String body = "<html><body>Please click this link to activate your account: <a href=\""+fullUrl+"\">"+fullUrl+"</a></body></html>";

        try
        {
            sendMimeMessage("Please activate your account", body, recipient );
        }
        catch (MessagingException | UnsupportedEncodingException e)
        {
            LOG.error("sendRegistrationEmail(): Sending registration mail to "+recipient+" failed",e);
            throw new RuntimeException("Sending mail failed");
        }
    }

    private static <C extends Page> String getAbsoluteUrl(final Class<C> pageClass, final PageParameters parameters)
    {
        final CharSequence result = RequestCycle.get().urlFor(new RenderPageRequestHandler(new PageProvider(pageClass, parameters)));

        final Url url = Url.parse(result.toString());
        String result2 = RequestCycle.get().getUrlRenderer().renderFullUrl(url);
        return result2.toString();
    }

    private void sendMimeMessage(String subject,String body,String recipient)
        throws MessagingException, UnsupportedEncodingException
    {
        String mailServer = "localhost";
        String sender = "root@localhost";

        final Properties props = System.getProperties();
        props.put("mail.smtp.host", mailServer);
        final Session session = Session.getInstance(props, null);
        final MimeMessage msg = new MimeMessage(session);
        msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
        msg.addHeader("format", "flowed");
        msg.addHeader("Content-Transfer-Encoding", "8bit");
        msg.setFrom(new InternetAddress(sender, ""));
        msg.setReplyTo(InternetAddress.parse(sender, false));
        msg.setSubject(subject, "UTF-8");
        msg.setText(body, "UTF-8");
        msg.setSentDate(new Date());
        msg.setRecipients(RecipientType.TO, InternetAddress.parse(recipient, false));
        javax.mail.Transport.send(msg);
    }

    @Resource
    public void setConfiguration(Configuration configuration)
    {
        this.configuration = configuration;
    }
}
