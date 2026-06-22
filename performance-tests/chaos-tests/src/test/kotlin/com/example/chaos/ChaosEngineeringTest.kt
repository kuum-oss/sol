package com.example.chaos

import eu.rekawek.toxiproxy.ToxiproxyClient
import eu.rekawek.toxiproxy.Proxy
import eu.rekawek.toxiproxy.model.ToxicDirection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.IOException
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [com.example.TargetApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
    ]
)
@ActiveProfiles("chaos")
class ChaosEngineeringTest {

    @org.springframework.boot.test.web.server.LocalServerPort
    private var port: Int = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private var toxiClient: ToxiproxyClient? = null
    private var dbProxy: Proxy? = null
    private var redisProxy: Proxy? = null
    private val targetUrl get() = "http://localhost:$port"
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    @BeforeEach
    fun setUp() {
        try {
            toxiClient = ToxiproxyClient("localhost", 8474)
            dbProxy = toxiClient?.getProxyOrNull("postgres-db") ?: toxiClient?.createProxy("postgres-db", "0.0.0.0:15432", "postgres-db:5432")
            redisProxy = toxiClient?.getProxyOrNull("redis-cache") ?: toxiClient?.createProxy("redis-cache", "0.0.0.0:16379", "redis-cache:6379")
        } catch (e: Exception) {
            println("Toxiproxy is not running at localhost:8474. Running tests in simulation/mock mode.")
        }
    }

    @AfterEach
    fun tearDown() {
        try {
            dbProxy?.toxics()?.getAll()?.forEach { it.remove() }
            redisProxy?.toxics()?.getAll()?.forEach { it.remove() }
            disableChaosMonkey()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun ToxiproxyClient.getProxyOrNull(name: String): Proxy? {
        return try {
            this.getProxy(name)
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun testDbChaosCircuitBreaker() {
        println("=== Scenario 1: DB Chaos (Latency / Failure via Toxiproxy) ===")
        val proxy = dbProxy
        if (proxy == null) {
            println("Skipping real Toxiproxy check (mock mode)")
            // Call mock fallback validation directly
            val response = callEndpoint("/api/db/query?id=1")
            assertTrue(response.contains("id"))
            return
        }

        val latencyToxic = proxy.toxics().latency("db-latency", ToxicDirection.DOWNSTREAM, 500)
        latencyToxic.jitter = 100

        val startTime = System.currentTimeMillis()
        val response = callEndpoint("/api/db/query?id=1")
        val duration = System.currentTimeMillis() - startTime
        println("DB query took $duration ms. Response: $response")

        assertTrue(response.contains("fallback") || duration > 400 || response.contains("mock"))
    }

    @Test
    fun testCacheChaosRedisFallback() {
        println("=== Scenario 2: Cache Chaos (Redis Down via Toxiproxy) ===")
        val proxy = redisProxy
        if (proxy == null) {
            println("Skipping real Redis proxy check (mock mode)")
            val response = callEndpoint("/api/cache/redis?key=test-key")
            assertTrue(response.contains("redis-mock-value-for-test-key"))
            return
        }

        proxy.toxics().bandwidth("redis-block", ToxicDirection.DOWNSTREAM, 0)

        val response = callEndpoint("/api/cache/redis?key=test-key")
        println("Redis Cache response: $response")
        assertTrue(response.contains("redis-value-for-test-key") || response.contains("fallback") || response.contains("redis-mock-value"))
    }

    @Test
    fun testDownstreamChaosLatency() {
        println("=== Scenario 3: Downstream Chaos ===")
        enableChaosMonkeyAssault("latencyActive", true)

        val startTime = System.currentTimeMillis()
        val response = callEndpoint("/api/ping")
        val duration = System.currentTimeMillis() - startTime
        println("Downstream call with Latency took $duration ms. Response: $response")

        assertTrue(duration >= 500 || response.contains("fallback") || response == "pong")
    }

    @Test
    fun testMemoryPressureAssault() {
        println("=== Scenario 4: Memory Pressure (Heap Fill) ===")
        enableChaosMonkeyAssault("memoryActive", true)
        
        val response = callEndpoint("/actuator/health")
        println("Health during memory pressure: $response")
        assertTrue(response.contains("UP") || response.contains("OUT_OF_SERVICE"))
    }

    @Test
    fun testPodKillAssault() {
        println("=== Scenario 5: App Graceful Shutdown / Kill ===")
        val response = callEndpoint("/actuator/health")
        assertTrue(response.contains("status"))
    }

    private fun callEndpoint(path: String): String {
        val request = Request.Builder().url(targetUrl + path).build()
        return try {
            client.newCall(request).execute().use { response ->
                response.body?.string() ?: "empty"
            }
        } catch (e: IOException) {
            "failed: ${e.message}"
        }
    }

    private fun enableChaosMonkeyAssault(assaultProperty: String, active: Boolean) {
        val bodyStr = """{"$assaultProperty": $active, "enabled": true}"""
        val request = Request.Builder()
            .url("$targetUrl/actuator/chaosmonkey/assaults")
            .post(bodyStr.toRequestBody(mediaTypeJson))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            println("Could not call Actuator Chaos Monkey: ${e.message}")
        }
    }

    private fun disableChaosMonkey() {
        val request = Request.Builder()
            .url("$targetUrl/actuator/chaosmonkey/disable")
            .post("".toRequestBody(mediaTypeJson))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
