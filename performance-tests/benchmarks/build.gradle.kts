plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.3"))
    implementation(project(":target-app"))
    implementation("org.springframework:spring-jdbc")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    implementation("org.openjdk.jmh:jmh-core:1.37")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}


tasks.register<JavaExec>("runBenchmarks") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.benchmarks.BenchmarkRunner")
    System.getProperty("jmh.regression.threshold.percent")?.let {
        systemProperty("jmh.regression.threshold.percent", it)
    }
    System.getProperty("jmh.result.file")?.let {
        systemProperty("jmh.result.file", it)
    }
    System.getProperty("jmh.baseline.file")?.let {
        systemProperty("jmh.baseline.file", it)
    }
    System.getProperty("jmh.update.baseline")?.let {
        systemProperty("jmh.update.baseline", it)
    }
}
