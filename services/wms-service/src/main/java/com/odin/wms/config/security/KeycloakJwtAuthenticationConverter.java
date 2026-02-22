package com.odin.wms.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a Keycloak-issued JWT into a Spring Security {@link JwtAuthenticationToken}.
 *
 * <p>Keycloak embeds roles in two places within the JWT payload:
 * <ul>
 *   <li>{@code realm_access.roles} — realm-level roles (e.g. wms-admin, wms-operator)</li>
 *   <li>{@code resource_access.wms-service.roles} — client-specific roles for this service</li>
 * </ul>
 *
 * <p>Each role is converted to a {@link SimpleGrantedAuthority} with prefix {@code ROLE_}
 * and the role name uppercased and with hyphens replaced by underscores.
 * For example, {@code wms-admin} becomes {@code ROLE_WMS_ADMIN}.
 */
@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String WMS_CLIENT_ID = "wms-service";
    private static final String ROLES_KEY = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Stream<String> realmRoles = Optional.ofNullable(jwt.getClaimAsMap(REALM_ACCESS_CLAIM))
                .map(ra -> (List<String>) ra.get(ROLES_KEY))
                .orElse(Collections.emptyList())
                .stream();

        Stream<String> clientRoles = Optional.ofNullable(jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM))
                .map(ra -> (Map<String, Object>) ra.get(WMS_CLIENT_ID))
                .map(client -> (List<String>) client.get(ROLES_KEY))
                .orElse(Collections.emptyList())
                .stream();

        Set<GrantedAuthority> authorities = Stream.concat(realmRoles, clientRoles)
                .distinct()
                .map(role -> new SimpleGrantedAuthority(toRoleName(role)))
                .collect(Collectors.toSet());

        return Collections.unmodifiableSet(authorities);
    }

    private String toRoleName(String role) {
        return "ROLE_" + role.toUpperCase().replace("-", "_");
    }
}
