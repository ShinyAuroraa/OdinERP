package br.com.odin.crm.web.config;

import br.com.odin.crm.infrastructure.keycloak.KeycloakAdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakAdminConfig {
}
