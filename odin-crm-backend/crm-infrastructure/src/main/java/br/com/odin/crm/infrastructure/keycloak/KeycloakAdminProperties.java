package br.com.odin.crm.infrastructure.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do service account para Keycloak Admin REST API.
 * O service account precisa das client roles manage-users e view-users
 * do client realm-management no realm odin-crm.
 *
 * <p>Propriedades:
 * <pre>
 * keycloak.admin.url             = ${KEYCLOAK_URL}
 * keycloak.admin.realm           = odin-crm
 * keycloak.admin.client-id       = ${KEYCLOAK_ADMIN_CLIENT_ID}
 * keycloak.admin.client-secret   = ${KEYCLOAK_ADMIN_CLIENT_SECRET}
 * </pre>
 */
@ConfigurationProperties("keycloak.admin")
public class KeycloakAdminProperties {

    private String url = "http://localhost:8180";
    private String realm = "odin-crm";
    private String clientId;
    private String clientSecret;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getRealm() { return realm; }
    public void setRealm(String realm) { this.realm = realm; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
}
