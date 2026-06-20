package com.example.load

import io.gatling.javaapi.core.*
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.http.*
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Duration

class TargetSimulation : Simulation() {

    private val httpProtocol = http
        .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling Load Generator")

    // CSV feeder containing user details for session simulation (JWT request, dynamic tags)
    private val feeder = csv("data/users.csv").random()

    private val scn = scenario("Target Application Load Scenario")
        .feed(feeder)
        // Simulate obtaining a JWT
        .exec(
            http("Authentication")
                .post("/api/ping") // Simplified auth ping
                .header("X-Auth-User", "#{username}")
                .check(status().`is`(200))
                .check(header("Set-Cookie").saveAs("sessionCookie"))
        )
        // Store session state JWT/Cookie and call serialization
        .exec { session ->
            session.set("jwtToken", "mock-jwt-token-for-${session.getString("username")}")
        }
        .exec(
            http("Jackson Serialization")
                .post("/api/serialize/jackson")
                .header("Authorization", "Bearer #{jwtToken}")
                .body(StringBody("""{"id":"#{username}","name":"User #{username}","tags":["gatling","test"],"rating":8.5}"""))
                .check(status().`is`(200))
        )
        .exec(
            http("Caffeine Cache Access")
                .get("/api/cache/caffeine")
                .queryParam("key", "#{username}")
                .check(status().`is`(200))
        )
        .exec(
            http("DB Query Endpoint")
                .get("/api/db/query")
                .queryParam("id", "1")
                .check(status().`is`(200))
        )
        .exec(
            http("CPU Intensive Task")
                .get("/api/algo/cpu")
                .queryParam("iterations", "500")
                .check(status().`is`(200))
        )

    init {
        // Retrieve profile from environment or system property, default is Smoke
        val profile = System.getProperty("profile", "smoke").lowercase()
        println("Running load test profile: $profile")

        val injectionProfile = when (profile) {
            "smoke" -> {
                // Smoke: 5 users, 1 min
                listOf(
                    atOnceUsers(5)
                )
            }
            "load" -> {
                // Load: Рабочая нагрузка 100-500 RPS, 10 min
                listOf(
                    rampUsersPerSec(10.0).to(100.0).during(Duration.ofMinutes(1)),
                    constantUsersPerSec(100.0).during(Duration.ofMinutes(8)),
                    rampUsersPerSec(100.0).to(500.0).during(Duration.ofMinutes(1))
                )
            }
            "stress" -> {
                // Stress: Ramp up to 2000 RPS to find limits
                listOf(
                    rampUsersPerSec(10.0).to(2000.0).during(Duration.ofMinutes(5))
                )
            }
            "soak" -> {
                // Soak: 200 RPS, 60 min
                listOf(
                    constantUsersPerSec(200.0).during(Duration.ofMinutes(60))
                )
            }
            "spike" -> {
                // Spike: 10 -> 1000 -> 10 RPS
                listOf(
                    constantUsersPerSec(10.0).during(Duration.ofSeconds(10)),
                    rampUsersPerSec(10.0).to(1000.0).during(Duration.ofSeconds(15)),
                    constantUsersPerSec(1000.0).during(Duration.ofSeconds(10)),
                    rampUsersPerSec(1000.0).to(10.0).during(Duration.ofSeconds(15))
                )
            }
            else -> throw IllegalArgumentException("Unknown load profile: $profile")
        }

        setUp(
            scn.injectOpen(injectionProfile)
        ).protocols(httpProtocol)
         .assertions(
             // Target and Critical SLA criteria
             global().responseTime().mean().lt(if (profile == "smoke") 1000 else 100),       // Target p50 < 100 ms
             global().responseTime().percentile(95.0).lt(if (profile == "smoke") 2000 else 300), // Target p95 < 300 ms
             global().responseTime().percentile(99.0).lt(if (profile == "smoke") 3000 else 800), // Target p99 < 800 ms
             global().failedRequests().percent().lt(0.1)     // Target error rate < 0.1%
         )
    }
}
