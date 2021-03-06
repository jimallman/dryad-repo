package org.dspace.app.xmlui.aspect.dryadfeedback;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.acting.AbstractAction;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.commons.lang.StringUtils;
import org.dspace.app.xmlui.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;

/**
 * @author Dan Leehr
 */

public class MembershipApplicationAction extends AbstractAction
{
    public Map act(Redirector redirector, SourceResolver resolver, Map objectModel,
            String source, Parameters parameters) throws Exception
    {
        Request request = ObjectModelHelper.getRequest(objectModel);

        String org_name = request.getParameter("org_name"); // required
        String org_legalname = request.getParameter("org_legalname");
        String org_type = request.getParameter("org_type");
        String org_annual_revenue = request.getParameter("org_annual_revenue"); // required
        String org_annual_revenue_currency = request.getParameter("org_annual_revenue_currency"); // required
        String billing_contact_name = request.getParameter("billing_contact_name"); // required
        String billing_email = request.getParameter("billing_email"); // required
        String billing_address = request.getParameter("billing_address"); // required
        String publications = request.getParameter("publications");
        String membership_year_start = request.getParameter("membership_year_start"); // required
        String membership_length = request.getParameter("membership_length"); // required
        String rep_name = request.getParameter("rep_name"); // required
        String rep_title = request.getParameter("rep_title"); // required
        String rep_email = request.getParameter("rep_email"); // required
        String comments = request.getParameter("comments");
        String submittedOnce = request.getParameter("submitted_once");
        String agent = request.getHeader("User-Agent");
        String session = request.getSession().getId();

        // User email from context
        Context context = ContextUtil.obtainContext(objectModel);

        if(     (org_name == null) || (org_name.equals("")) ||
                (org_annual_revenue == null) || (org_annual_revenue.equals("")) ||
                (org_annual_revenue_currency == null) || (org_annual_revenue_currency.equals("")) ||
                (billing_contact_name == null) || (billing_contact_name.equals("")) ||
                (billing_address == null) || (billing_address.equals("")) ||
                (billing_email == null) || (billing_email.equals("")) ||
                (membership_year_start == null) ||
                (membership_length == null) ||
                (rep_name == null) || (rep_name.equals("")) ||
                (rep_email == null) || (rep_email.equals(""))
                ) {
            // Either this is the first request for the form, or it has been 
            // submitted without required parameters
            Map<String, String> map = new HashMap<String, String>();
            map.put("org_name", org_name);
            map.put("org_annual_revenue", org_annual_revenue);
            map.put("org_annual_revenue_currency", org_annual_revenue_currency);
            map.put("org_legalname", org_legalname);
            map.put("org_type", org_type);

            map.put("billing_contact_name", billing_contact_name);
            map.put("billing_email", billing_email);
            map.put("billing_address", billing_address);
            
            map.put("publications", publications);
            map.put("membership_year_start", membership_year_start);
            map.put("membership_length", membership_length);

            map.put("rep_name", rep_name);
            map.put("rep_email", rep_email);

            map.put("comments", comments);

            // Handle error fields on submission
            List<String> errorFieldNames = new ArrayList<String>();

            if((org_name != null) && org_name.equals("")) {
                errorFieldNames.add("org_name");
            }
            if((org_type != null) && org_type.equals("")) {
                errorFieldNames.add("org_type");
            }
            if((submittedOnce != null) && ((org_annual_revenue == null) || org_annual_revenue.equals(""))) {
                errorFieldNames.add("org_annual_revenue");
            }
            if((submittedOnce != null) && ((org_annual_revenue_currency == null) || org_annual_revenue_currency.equals(""))) {
                errorFieldNames.add("org_annual_revenue_currency");
            }
            if((billing_contact_name != null) && billing_contact_name.equals("")) {
                errorFieldNames.add("billing_contact_name");
            }
            if((billing_email != null) && billing_email.equals("")) {
                errorFieldNames.add("billing_email");
            }
            if((billing_address != null) && billing_address.equals("")) {
                errorFieldNames.add("billing_address");
            }
            if((submittedOnce != null) && ((membership_year_start == null) || membership_year_start.equals(""))) {
                errorFieldNames.add("membership_year_start");
            }
            if((submittedOnce != null) && ((membership_length == null) || membership_length.equals(""))) {
                errorFieldNames.add("membership_length");
            }
            if((rep_name != null) && rep_name.equals("")) {
                errorFieldNames.add("rep_name");
            }
            if((rep_email != null) && rep_email.equals("")) {
                errorFieldNames.add("rep_email");
            }

            if(errorFieldNames.size() > 0) {
                // missing required fields
                map.put("error_fields",StringUtils.join(errorFieldNames.toArray(), ','));
            }
            return map;
        }

        String fromPage = request.getHeader("Referer");

        // Prevent spammers and splogbots from submitting the form
        String host = ConfigurationManager.getProperty("dspace.hostname");
        String allowedReferrersString = ConfigurationManager.getProperty("mail.allowed.referrers");

        String[] allowedReferrersSplit = null;
        boolean validReferral = false;

        if((allowedReferrersString != null) && (allowedReferrersString.length() > 0))
        {
            allowedReferrersSplit = allowedReferrersString.trim().split("\\s*,\\s*");
            for(int i = 0; i < allowedReferrersSplit.length; i++)
            {
                if(fromPage.indexOf(allowedReferrersSplit[i]) != -1)
                {
                    validReferral = true;
                    break;
                }
            }
        }

        String basicHost = "";
        if (host.equals("localhost") || host.equals("127.0.0.1")
                        || host.equals(InetAddress.getLocalHost().getHostAddress()))
            basicHost = host;
        else
        {
            // cut off all but the hostname, to cover cases where more than one URL
            // arrives at the installation; e.g. presence or absence of "www"
            int lastDot = host.lastIndexOf(".");
            int dotBeforeLast = host.substring(0, lastDot).lastIndexOf(".");

            if(dotBeforeLast < 0)
            {
            basicHost = host;
            }
            else
            {
                basicHost = host.substring(dotBeforeLast);
            }
        }


        // All data is there, send the email
        Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(context.getCurrentLocale(), "membership_application"));
        email.addRecipient(ConfigurationManager
                .getProperty("membership.recipient"));

        email.addArgument(new Date());
        email.addArgument(org_name);
        email.addArgument(org_legalname);
        email.addArgument(org_type);
        email.addArgument(org_annual_revenue);
        email.addArgument(org_annual_revenue_currency);
        email.addArgument(billing_contact_name);
        email.addArgument(billing_email);
        email.addArgument(billing_address);
        email.addArgument(publications);
        email.addArgument(membership_year_start);
        email.addArgument(membership_length);
        email.addArgument(rep_name);
        email.addArgument(rep_title);
        email.addArgument(rep_email);
        email.addArgument(comments);
        email.addArgument(agent);
        email.addArgument(session);

        // May generate MessageExceptions.
        email.send();

        // Finished, allow to pass.
        return null;
    }

}
