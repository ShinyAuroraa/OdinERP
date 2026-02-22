package br.com.odin.crm.web.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('crm-vendedor','crm-gerente','crm-sac','crm-financeiro','crm-admin')")
    public ResponseEntity<MeResponse> me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        List<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toList());
        return ResponseEntity.ok(new MeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("name"),
                roles
        ));
    }
}
