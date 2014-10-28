package org.dspace.authenticate;

import org.apache.log4j.Logger;
import org.apache.tools.ant.taskdefs.Get;
import org.dspace.authority.orcid.Orcid;
import org.dspace.authority.orcid.model.Bio;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.eperson.EPerson;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;

/**
 *
 * @author mdiggory at atmire.com
 */
public class OAuthAuthenticationMethod implements AuthenticationMethod{

    /** log4j category */
    private static Logger log = Logger.getLogger(OAuthAuthenticationMethod.class);

    @Override
    public boolean canSelfRegister(Context context, HttpServletRequest request, String username) throws SQLException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void initEPerson(Context context, HttpServletRequest request, EPerson eperson) throws SQLException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean allowSetPassword(Context context, HttpServletRequest request, String username) throws SQLException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isImplicit() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int[] getSpecialGroups(Context context, HttpServletRequest request) throws SQLException {
        return new int[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int authenticate(Context context, String username, String password, String realm, HttpServletRequest request) throws SQLException {


        String email = null;

        String orcid = (String) request.getAttribute("orcid");
        String token = (String) request.getAttribute("access_token");
        String refreshToken = (String) request.getAttribute("refresh_token");
        boolean test = "true".equals(request.getParameter("test"));
        if (request == null||orcid==null)
        {
            return BAD_ARGS;
        }

        //get the orcid profile
        Bio bio = null;
        Orcid orcidObject = Orcid.getOrcid();
        if(orcid!=null)
        {
            if(token==null||test){
                bio = orcidObject.getBio(orcid);
            }
            else
            {
                bio = orcidObject.getBio(orcid,token);
            }
        }
        //get the email from orcid
        if(bio!=null)
        {
            email = bio.getEmail();

        }


        //Get ORCID profile if they are authenticated (test mode = ?test=true&orcid=[ID]
        //use [orcid]@test.com as the email for test mode
        if(test)
        {
            email = orcid+"@test.com";
        }

        EPerson currentUser = context.getCurrentUser();


        if(orcid!=null)
        {
            //Step 1, check if the orcid id exists or not
            EPerson e = EPerson.findByOrcidId(context,orcid);

            if(e!=null)
            {
               //orcid id already exists

               if(currentUser!=null&&e.getID()==currentUser.getID())
               {
                   //the orcid id already linked to the current user
                   request.getSession().setAttribute("oauth.authenticated",
                           Boolean.TRUE);
                   return AuthenticationMethod.SUCCESS;
               }
               else if(currentUser==null)
               {
                   //the orcid id exists and already linked to the user ,login successful
                   request.getSession().setAttribute("oauth.authenticated",
                           Boolean.TRUE);
                   context.setCurrentUser(e);
                   return AuthenticationMethod.SUCCESS;
               }
               else
               {
                   //todo:report the exist orcid user
                   request.getSession().setAttribute("exist_orcid",e.getEmail());
                   return BAD_CREDENTIALS;
               }


            }



            //Step 2, check the current login user
            if (currentUser != null)
            {
                if(email!=null)
                {
                    if(!email.equals(currentUser.getEmail() ))
                    {
                        //current user has a different email address than the orcid user
                        request.getSession().setAttribute("exist_email",email);
                        return BAD_CREDENTIALS;
                    }
                }
                else {
                    email = currentUser.getEmail();
                }
                //link the orcid id to the current login user
                currentUser.setMetadata("orcid",orcid);
                currentUser.setMetadata("access_token",token);
                orcid = currentUser.getMetadata("orcid");
                try{
                currentUser.update();
                }catch (Exception exception)
                {
                    log.error("error when link the orcid id:"+orcid+" to current login in user:"+currentUser.getEmail());
                }
                context.commit();
                request.getSession().setAttribute("oauth.authenticated",
                        Boolean.TRUE);
                return AuthenticationMethod.SUCCESS;
            }

            //step 3, check the email
            if(email!=null)
            {
                email = email.toLowerCase();
                try{
                    EPerson ePerson = EPerson.findByEmail(context,email);
                    if(ePerson!=null)
                    {
                        //found the eperson by email. link it with orcid
                        context.setCurrentUser(ePerson);
                        ePerson.setMetadata("orcid",orcid);
                        ePerson.setMetadata("access_token",token);
                        ePerson.update();
                        context.commit();
                        request.getSession().setAttribute("oauth.authenticated",
                                Boolean.TRUE);
                        return AuthenticationMethod.SUCCESS;

                    }
                    else
                    {
                        context.turnOffAuthorisationSystem();
                        String fname = "";
                        if (bio != null)
                        {
                            // try to grab name from the orcid profile
                            fname = bio.getName().getGivenNames();

                        }
                        String lname = "";
                        if (bio != null)
                        {
                            // try to grab name from the orcid profile
                            lname = bio.getName().getFamilyName();
                        }
                        //create new eperson with the email address
                        EPerson newEPerson = EPerson.create(context);
                        newEPerson.setEmail(email);
                        if (fname != null)
                        {
                            newEPerson.setFirstName(fname);
                        }
                        if (lname != null)
                        {
                            newEPerson.setLastName(lname);
                        }
                        newEPerson.setCanLogIn(true);
                        AuthenticationManager.initEPerson(context, request, newEPerson);
                        newEPerson.setMetadata("orcid",orcid);
                        newEPerson.setMetadata("access_token",token);
                        newEPerson.update();
                        context.commit();
                        context.setCurrentUser(newEPerson);
                        context.restoreAuthSystemState();

                        return AuthenticationMethod.SUCCESS;
                    }
                }catch (Exception ex)
                {
                    log.error("error when try to link orcid:"+orcid+" to eperson:"+email);
                    return BAD_CREDENTIALS;
                }

            }
            else
            {
               //no email fetched from orcid,need check the privacy setup
                request.getSession().setAttribute("set_orcid",orcid);
                return BAD_CREDENTIALS;
            }

        }
        else
        {
            return BAD_CREDENTIALS;
        }


    }
    @Override
    public String loginPageURL(Context context, HttpServletRequest request, HttpServletResponse response) {
        if(ConfigurationManager.getBooleanProperty("authentication-oauth","choice-page")){
            return response.encodeRedirectURL(request.getContextPath()
                + "/oauth-login");
        }
        else
        {
            return null;
        }
    }

    @Override
    public String loginPageTitle(Context context) {
        return "org.dspace.authenticate.OAuthAuthentication.title";
    }
}
