package uk.org.teessidehackspace.keycloak.provider;

import org.jboss.logging.Logger;
import com.auth0.client.auth.AuthAPI;
import com.auth0.exception.APIException;
import com.auth0.exception.Auth0Exception;
import com.auth0.net.TokenRequest;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;

import java.util.HashMap;
import java.util.Map;

public class Auth0UserStorageProvider implements UserStorageProvider, CredentialInputValidator, UserLookupProvider {

    private static final Logger logger = Logger.getLogger(Auth0UserStorageProvider.class);

    protected Map<String, UserModel> loadedUsers = new HashMap<>();

    private final ComponentModel model;

    public Auth0UserStorageProvider(KeycloakSession session, ComponentModel model) {
        this.model = model;
    }

    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!(input instanceof UserCredentialModel)) return false;
        if (input.getType().equals(PasswordCredentialModel.TYPE)) {
            return validPassword(realm, user, input.getChallengeResponse());
        } else {
            return false;
        }
    }

    public boolean validPassword(RealmModel realm, UserModel user, String password) {
        AuthAPI api = AuthAPI.newBuilder(model.get("auth0_domain"), model.get("auth0_client_id"), model.get("auth0_client_secret"))
                .build();
        
        TokenRequest request = api.login(user.getUsername(), password.toCharArray())
                .setAudience("https://"+model.get("auth0_domain")+"/api/v2/")
                .setScope("openid");
        try {
            request.execute();
            user.credentialManager().updateCredential(UserCredentialModel.password(password));
            user.addRequiredAction(UpdateProfileWithUsernameValidation.PROVIDER_ID);
            user.setFederationLink(null);
            return true;
        } catch (APIException exception) {
            logger.error(exception);
        } catch (Auth0Exception exception) {
            logger.error(exception);
        }
        return false;
    }

    public void close() {
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        StorageId storageId = new StorageId(id);
        String username = storageId.getExternalId();
        return getUserByUsername(realm, username);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return loadedUsers.get(username);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return getUserByUsername(realm, email);
    }
}
