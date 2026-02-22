plugins {
    `java-library`
}

dependencies {
    // Domain dependency exposed transitively to consumers
    api(project(":crm-domain"))

    // Build tooling
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // API — exposed transitively (use cases depend on validation)
    api(libs.jakarta.validation.api)

    // Testing — explicit versions (no Spring BOM in this module)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
