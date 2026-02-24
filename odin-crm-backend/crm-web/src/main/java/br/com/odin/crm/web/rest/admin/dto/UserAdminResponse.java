package br.com.odin.crm.web.rest.admin.dto;

import java.util.List;

public record UserAdminResponse(
        String keycloakId,
        String nome,
        String email,
        String cargo,
        boolean enabled,
        List<String> roles,
        String lastLogin
) {}
