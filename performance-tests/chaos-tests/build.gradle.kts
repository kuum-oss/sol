plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":target-app"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    
    // Toxiproxy Java Client
    implementation("eu.rekawek.toxiproxy:toxiproxy-java:2.1.11")
    
    // HTTP client to configure target-app and verify status
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

tasks.test {
    useJUnitPlatform()
}
