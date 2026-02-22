plugins {
    `java-library`
}

dependencies {
    // Build tooling
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing — explicit versions (no Spring BOM in this module)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
