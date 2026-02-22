plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // All layers
    implementation(project(":crm-infrastructure"))

    // Build tooling — MapStruct processor must be declared before lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.lombok)

    // Web
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.oauth2)

    // gRPC server transport
    implementation(libs.grpc.netty.shaded)

    // Observability
    implementation(libs.micrometer.tracing.otel)
    implementation(libs.logstash.logback)

    // Mapping
    implementation(libs.mapstruct)

    // Testing
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
