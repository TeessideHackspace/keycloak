package uk.org.teessidehackspace.keycloak.provider;

import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.events.Details;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

import static org.keycloak.services.validation.Validation.FIELD_USERNAME;

public class UpdateProfileWithUsernameValidation implements RequiredActionProvider, RequiredActionFactory {

    public static final String PROVIDER_ID = "update-profile-validate-username";

    public static final String FIELD_LAST_NAME = "lastName";
    public static final String FIELD_FIRST_NAME = "firstName";
    public static final String FIELD_EMAIL = "email";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        Response challenge = context.form()
                .createResponse(UserModel.RequiredAction.UPDATE_PROFILE);
        context.challenge(challenge);
    }

    private static void addError(List<FormMessage> errors, String field, String message){
        errors.add(new FormMessage(field, message));
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    @Override
    public void processAction(RequiredActionContext context) {
        EventBuilder event = context.getEvent();
        event.event(EventType.UPDATE_PROFILE);
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        UserModel user = context.getUser();
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        List<FormMessage> errors = new ArrayList<>();
        
        if (isBlank(formData.getFirst(FIELD_USERNAME))) {
            addError(errors, FIELD_USERNAME, Messages.MISSING_USERNAME);
        } else if(!formData.getFirst(FIELD_USERNAME).matches("[A-Za-z0-9_-]+")) {
            addError(errors, FIELD_USERNAME, Messages.INVALID_USERNAME);
        }

        if (isBlank(formData.getFirst(FIELD_FIRST_NAME))) {
            addError(errors, FIELD_FIRST_NAME, Messages.MISSING_FIRST_NAME);
        }

        if (isBlank(formData.getFirst(FIELD_LAST_NAME))) {
            addError(errors, FIELD_LAST_NAME, Messages.MISSING_LAST_NAME);
        }

        if (isBlank(formData.getFirst(FIELD_EMAIL))) {
            addError(errors, FIELD_EMAIL, Messages.MISSING_EMAIL);
        } else if (!Validation.isEmailValid(formData.getFirst(FIELD_EMAIL))) {
            addError(errors, FIELD_EMAIL, Messages.INVALID_EMAIL);
        }
        if (errors != null && !errors.isEmpty()) {
            Response challenge = context.form()
                    .setErrors(errors)
                    .setFormData(formData)
                    .createResponse(UserModel.RequiredAction.UPDATE_PROFILE);
            context.challenge(challenge);
            return;
        }
        
        if (realm.isEditUsernameAllowed()) {
            String username = formData.getFirst("username");
            String oldUsername = user.getUsername();

            boolean usernameChanged = oldUsername != null ? !oldUsername.equals(username) : username != null;

            if (usernameChanged) {
                if (session.users().getUserByUsername(realm, username) != null) {
                    Response challenge = context.form()
                            .setError(Messages.USERNAME_EXISTS)
                            .setFormData(formData)
                            .createResponse(UserModel.RequiredAction.UPDATE_PROFILE);
                    context.challenge(challenge);
                    return;
                }

                user.setUsername(username);
            }
        }

        user.setFirstName(formData.getFirst("firstName"));
        user.setLastName(formData.getFirst("lastName"));

        String email = formData.getFirst("email");

        String oldEmail = user.getEmail();
        boolean emailChanged = oldEmail != null ? !oldEmail.equals(email) : email != null;

        if (emailChanged) {
            if (!realm.isDuplicateEmailsAllowed()) {
                UserModel userByEmail = session.users().getUserByEmail(realm, email);

                // check for duplicated email
                if (userByEmail != null && !userByEmail.getId().equals(user.getId())) {
                    Response challenge = context.form()
                            .setError(Messages.EMAIL_EXISTS)
                            .setFormData(formData)
                            .createResponse(UserModel.RequiredAction.UPDATE_PROFILE);
                    context.challenge(challenge);
                    return;
                }
            }

            user.setEmail(email);
            user.setEmailVerified(false);
        }

        user.setAttribute("nickName", formData.get("user.attributes.nickName"));

        if (emailChanged) {
            event.clone().event(EventType.UPDATE_EMAIL).detail(Details.PREVIOUS_EMAIL, oldEmail).detail(Details.UPDATED_EMAIL, email).success();
        }
        context.success();
    }


    @Override
    public void close() {

    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }


    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public String getDisplayText() {
        return "Update Profile With Username Validation";
    }


    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
