package davmail.exchange.auth;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.util.Cookie;
import davmail.BundleMessage;
import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class HtmlUnitFormAuthenticator implements ExchangeAuthenticator {

    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.ExchangeSession");

    //Custom form fields
    private String usernameFieldNameOrId="UserName";
    private String passwordFieldNameOrId="Password";
    private String submitButtonId="submitButton";
    private String validAuthenticationCookieName="FedAuth";

    //Form data
    private String username;
    private String password;
    private String url;

    //Store cookies for old http recreation...
    private Set<com.gargoylesoftware.htmlunit.util.Cookie> authCookies;

    @Override
    public void authenticate() throws IOException {

        final WebClient webClient = new WebClient();
        try {

            //webClient.getOptions().setUseInsecureSSL(true);
            webClient.setAjaxController(new NicelyResynchronizingAjaxController());
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(true);
            webClient.getOptions().setThrowExceptionOnScriptError(true);
            webClient.waitForBackgroundJavaScript(1500);
            webClient.waitForBackgroundJavaScriptStartingBefore(1500);
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setJavaScriptEnabled(true);
            webClient.getOptions().setRedirectEnabled(true);

            // Get the first page
            final HtmlPage loginPage = webClient.getPage(url);

            synchronized (loginPage) {
                try {
                    loginPage.wait(1500);
                } catch (InterruptedException e) {
                    throw new IOException("Error waiting for loginpage " + url, e);
                }
            }

            // Get the form that we are dealing with and within that form,
            // find the submit button and the field that we want to change.
            //final HtmlForm form = loginPage.getHtmlElementById("loginForm");

            final HtmlElement button = loginPage.getHtmlElementById(submitButtonId);
            final HtmlInput usernameField = (HtmlInput) loginPage.getElementsByIdAndOrName(usernameFieldNameOrId).get(0);
            final HtmlInput passwordField = (HtmlInput) loginPage.getElementsByIdAndOrName(passwordFieldNameOrId).get(0);

            // Change the value of the text field
            usernameField.type(username);
            passwordField.type(password);

            // Now submit the form by clicking the button and get back the second page.
            final HtmlPage loginPage2 = button.click();

            synchronized (loginPage2) {
                try {
                    loginPage2.wait(1500);
                } catch (InterruptedException e) {
                    throw new IOException("Error waiting for loginpage2 " + url, e);
                }
            }

            //Copy cookies for later...
            authCookies = new HashSet<Cookie>(webClient.getCookies(new URL(url)));

        }
        catch (ElementNotFoundException e)
        {
            LOGGER.error(BundleMessage.formatLog("EXCEPTION_EXCHANGE_LOGIN_FAILED", e));
            throw new DavMailException("EXCEPTION_EXCHANGE_LOGIN_FAILED", e);
        }

        finally {
            webClient.close();
        }


        //Check auth
        boolean authenticated = false;
        for(Cookie cookie : authCookies)
        {
            if("FedAuth".equals(cookie.getName()))
            {
                authenticated=true;
                break;
            }
        }

        if(!authenticated)
        {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED");
        }

        LOGGER.debug("Successfully authenticated to " + url);

    }

    public org.apache.commons.httpclient.HttpClient getHttpClientAdapter() throws DavMailException {
        org.apache.commons.httpclient.HttpClient oldHttpClient = null;
        oldHttpClient = DavGatewayHttpClientFacade.getInstance(url);
        DavGatewayHttpClientFacade.setCredentials(oldHttpClient, username, password);
        DavGatewayHttpClientFacade.createMultiThreadedHttpConnectionManager(oldHttpClient);

        for (Cookie cookie : authCookies) {
            org.apache.commons.httpclient.Cookie oldCookie = new org.apache.commons.httpclient.Cookie(
                    cookie.getDomain(),
                    cookie.getName(),
                    cookie.getValue(),
                    cookie.getPath(),
                    cookie.getExpires(),
                    cookie.isSecure());
            oldCookie.setPathAttributeSpecified(cookie.getPath() != null);
            oldHttpClient.getState().addCookie(oldCookie);
        }

        return oldHttpClient;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;

    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    public void setUrl(String url) {
        this.url = url;
    }


    @Override
    public O365Token getToken() throws IOException {
        return null;
    }

    @Override
    public URI getExchangeUri() {
        return URI.create(url);
    }

    public void setUsernameFieldNameOrId(String usernameFieldNameOrId) {
        this.usernameFieldNameOrId = usernameFieldNameOrId;
    }

    public void setPasswordFieldNameOrId(String passwordFieldNameOrId) {
        this.passwordFieldNameOrId = passwordFieldNameOrId;
    }

    public void setSubmitButtonId(String submitButtonId) {
        this.submitButtonId = submitButtonId;
    }

    public void setValidAuthenticationCookieName(String validAuthenticationCookieName) {
        this.validAuthenticationCookieName = validAuthenticationCookieName;
    }

    public String getUsername() {
        return username;
    }
}
