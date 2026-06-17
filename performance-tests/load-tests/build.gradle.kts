plugins {
    kotlin("jvm")
}

sourceSets {
    main {
        kotlin.srcDir("src/gatling/kotlin")
        resources.srcDir("src/gatling/resources")
    }
}

dependencies {
    implementation("io.gatling.highcharts:gatling-charts-highcharts:3.11.3")
    implementation("io.gatling:gatling-app:3.11.3")
}

tasks.register<JavaExec>("runLoadTest") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.gatling.app.Gatling")

    // Pass system properties to Gatling execution
    systemProperties(System.getProperties().mapKeys { it.key.toString() })

    args(
        "-s", "com.example.load.TargetSimulation",
        "-rf", "${project.rootDir}/reports/gatling"
    )
}