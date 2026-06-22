plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.code.gson:gson:2.10.1")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // HTTP client
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Chaos Engineering
    implementation("de.codecentric:chaos-monkey-spring-boot:3.1.0")
    
    // Resilience (Circuit Breaker)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
