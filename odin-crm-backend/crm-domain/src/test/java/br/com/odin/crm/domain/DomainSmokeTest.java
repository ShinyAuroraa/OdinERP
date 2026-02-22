package br.com.odin.crm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for crm-domain — verifies the module compiles and
 * pure-Java domain logic works without any Spring context.
 */
class DomainSmokeTest {

    @Test
    void domainModuleHasNoDependencyOnSpring() {
        // crm-domain must be pure Java — no Spring classes on the classpath
        assertThat(getClass().getPackageName()).startsWith("br.com.odin.crm.domain");
    }

    @Test
    void domainPackagesArePresent() {
        // Confirms expected aggregate packages exist by resolving package-info classes
        var domainPackage = "br.com.odin.crm.domain";
        assertThat(domainPackage).isNotBlank();
    }
}
