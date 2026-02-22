plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

tasks.withType<Test>().configureEach {
    // Docker Desktop 29.x rejects docker-java API version ≤1.43 with HTTP 400.
    // TC's shaded docker-java-core reads the version via the "api.version" JVM system property
    // (NOT via DOCKER_API_VERSION env var). Minimum accepted by Docker Desktop 29.x is 1.44.
    // DOCKER_API_VERSION env var override allows CI/CD to pin a specific version if needed.
    val dockerApiVersion = System.getenv("DOCKER_API_VERSION") ?: "1.46"
    systemProperty("api.version", dockerApiVersion)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    // Application layer
    api(project(":crm-application"))

    // Build tooling
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Persistence
    implementation(libs.spring.data.jpa)
    implementation(libs.spring.data.redis)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    // Messaging
    implementation(libs.spring.kafka)

    // gRPC stubs (runtime transport provided by crm-web)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)

    // Resilience
    implementation(libs.resilience4j.spring)

    // Search
    implementation(libs.opensearch.java)

    // Testing
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    runtimeOnly(libs.postgresql)
}
