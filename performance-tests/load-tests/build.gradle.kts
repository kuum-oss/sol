import java.net.HttpURLConnection
import java.net.URI
import java.io.File
import java.util.concurrent.TimeUnit

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

var targetAppProcess: Process? = null

tasks.register("startTargetApp") {
    dependsOn(":target-app:bootJar")
    doLast {
        val bootJarTask = project(":target-app").tasks.getByName("bootJar") as org.gradle.api.tasks.bundling.Jar
        val jarFile = bootJarTask.archiveFile.get().asFile.absolutePath
        println("Starting target-app from bootJar: $jarFile")

        val logsDir = File(project.rootDir, "build/logs").apply { mkdirs() }
        val logFile = File(logsDir, "target-app-boot.log")
        val errFile = File(logsDir, "target-app-boot-err.log")

        val process = ProcessBuilder("java", "-jar", jarFile)
            .redirectOutput(ProcessBuilder.Redirect.to(logFile))
            .redirectError(ProcessBuilder.Redirect.to(errFile))
            .start()
        
        targetAppProcess = process

        println("Waiting for target-app to start on http://localhost:8080/api/ping...")
        var connected = false
        val startTime = System.currentTimeMillis()
        val timeout = 90000 // 90 seconds
        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                val connection = URI("http://localhost:8080/api/ping").toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 1000
                connection.readTimeout = 1000
                if (connection.responseCode == 200) {
                    connected = true
                    break
                }
            } catch (e: Exception) {
                // ignore
            }
            Thread.sleep(1000)
        }

        if (!connected) {
            process.destroyForcibly()
            throw GradleException("target-app did not start in time. Check logs at ${logFile.absolutePath}")
        }
        println("target-app started successfully!")
    }
}

tasks.register("stopTargetApp") {
    doLast {
        val process = targetAppProcess
        if (process != null) {
            println("Stopping target-app...")
            process.destroy()
            process.waitFor(5, TimeUnit.SECONDS)
            if (process.isAlive) {
                process.destroyForcibly()
            }
            println("target-app stopped.")
        }
    }
}

tasks.register<JavaExec>("runLoadTest") {
    dependsOn("startTargetApp")
    finalizedBy("stopTargetApp")

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.gatling.app.Gatling")
    systemProperties(System.getProperties().mapKeys { it.key.toString() })
    args(
        "-s", "com.example.load.TargetSimulation",
        "-rf", "${project.rootDir}/reports/gatling"
    )
}