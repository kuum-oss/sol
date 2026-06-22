package com.example.load

import io.gatling.javaapi.core.*
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.http.*
import io.gatling.javaapi.http.HttpDsl.*
import java.time.Duration
import java.util.UUID

class TargetSimulation : Simulation() {

    private val httpProtocol = http
        .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling Load Generator")

    // Smoke-профиль: небольшой фиксированный набор пользователей из CSV —
    // достаточен при малом RPS и позволяет проверить конкретных пользователей.
    private val csvFeeder = csv("data/users.csv").random()

    // Load / Stress / Soak / Spike профили: динамический фидер с UUID.
    // Каждая виртуальная сессия получает уникальный ключ → гарантированный
    // Cache Miss в Caffeine и Redis → реалистичное измерение производительности.
    private val dynamicFeeder = generateSequence {
        val id = UUID.randomUUID().toString()
        mapOf(
            "username" to "user-$id",
            "itemId"   to (1..3).random().toString()   // БД: id 1..3 (из миграции V1)
        )
    }.iterator()

    private fun buildScenario(feeder: Iterator<Map<String, Any>>): ScenarioBuilder =
        scenario("Target Application Load Scenario")
            .feed(feeder)
            // Симуляция получения JWT-токена
            .exec(
                http("Authentication")
                    .post("/api/ping")
                    .header("X-Auth-User", "#{username}")
                    .check(status().`is`(200))
                    .check(header("Set-Cookie").saveAs("sessionCookie"))
            )
            .exec { session ->
                session.set("jwtToken", "mock-jwt-token-for-${session.getString("username")}")
            }
            .exec(
                http("Jackson Serialization")
                    .post("/api/serialize/jackson")
                    .header("Authorization", "Bearer #{jwtToken}")
                    // UUID-имя пользователя → уникальное тело запроса каждый раз
                    .body(StringBody("""{"id":"#{username}","name":"User #{username}","tags":["gatling","test"],"rating":8.5}"""))
                    .check(status().`is`(200))
            )
            .exec(
                http("Caffeine Cache Access")
                    .get("/api/cache/caffeine")
                    // UUID-ключ → гарантированный Cache Miss в Caffeine при первом обращении
                    .queryParam("key", "#{username}")
                    .check(status().`is`(200))
            )
            .exec(
                http("DB Query Endpoint")
                    .get("/api/db/query")
                    // itemId варьируется 1..3 согласно реальным данным в БД
                    .queryParam("id", "#{itemId}")
                    .check(status().`is`(200))
            )
            .exec(
                http("CPU Intensive Task")
                    .get("/api/algo/cpu")
                    .queryParam("iterations", "500")
                    .check(status().`is`(200))
            )

    init {
        val profile = System.getProperty("profile", "smoke").lowercase()
        println("Running load test profile: $profile")

        val injectionProfile = when (profile) {
            "smoke" -> listOf(atOnceUsers(5))

            "load" -> listOf(
                rampUsersPerSec(10.0).to(100.0).during(Duration.ofMinutes(1)),
                constantUsersPerSec(100.0).during(Duration.ofMinutes(8)),
                rampUsersPerSec(100.0).to(500.0).during(Duration.ofMinutes(1))
            )

            "stress" -> listOf(
                rampUsersPerSec(10.0).to(2000.0).during(Duration.ofMinutes(5))
            )

            "soak" -> listOf(
                constantUsersPerSec(200.0).during(Duration.ofMinutes(60))
            )

            "spike" -> listOf(
                constantUsersPerSec(10.0).during(Duration.ofSeconds(10)),
                rampUsersPerSec(10.0).to(1000.0).during(Duration.ofSeconds(15)),
                constantUsersPerSec(1000.0).during(Duration.ofSeconds(10)),
                rampUsersPerSec(1000.0).to(10.0).during(Duration.ofSeconds(15))
            )

            else -> throw IllegalArgumentException("Unknown load profile: $profile")
        }

        // Smoke использует CSV (фиксированный набор), остальные — UUID-фидер
        val feeder = if (profile == "smoke") csvFeeder.asInstanceOf() else dynamicFeeder
        val scn = buildScenario(feeder)

        setUp(
            scn.injectOpen(injectionProfile)
        ).protocols(httpProtocol)
         .assertions(
             global().responseTime().mean().lt(if (profile == "smoke") 1000 else 100),
             global().responseTime().percentile(95.0).lt(if (profile == "smoke") 2000 else 300),
             global().responseTime().percentile(99.0).lt(if (profile == "smoke") 3000 else 800),
             global().failedRequests().percent().lt(0.1)
         )
    }
}

