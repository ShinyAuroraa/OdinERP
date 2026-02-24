package br.com.odin.crm.infrastructure.user;

import java.util.List;

/**
 * DTO de transferência de dados de usuário entre UserAdminService e UserAdminController.
 */
public record UserAdminDto(
        String keycloakId,
        String nome,
        String email,
        String cargo,
        boolean enabled,
        List<String> roles,
        String lastLogin
) {}
