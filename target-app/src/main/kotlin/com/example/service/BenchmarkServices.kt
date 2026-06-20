package com.example.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import jakarta.annotation.PostConstruct
import java.time.Duration
import java.util.concurrent.TimeUnit

@Serializable
data class Payload(val id: String, val name: String, val tags: List<String>, val rating: Double)

@Service
class BenchmarkService(
    @Autowired(required = false) val jdbcTemplate: JdbcTemplate?,
    @Autowired(required = false) val redisTemplate: StringRedisTemplate?
) {
    @PostConstruct
    fun initDb() {
        try {
            jdbcTemplate?.execute("CREATE TABLE IF NOT EXISTS items (id SERIAL PRIMARY KEY, name VARCHAR(255))")
            val count = jdbcTemplate?.queryForObject("SELECT COUNT(*) FROM items", Int::class.java) ?: 0
            if (count == 0) {
                jdbcTemplate?.execute("INSERT INTO items (id, name) VALUES (1, 'Item 1'), (2, 'Item 2'), (3, 'Item 3')")
            }
        } catch (e: Exception) {
            println("Failed to initialize database: ${e.message}")
        }
    }
    private val gson = Gson()
    private val caffeineCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build<String, String>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    private val webClient = WebClient.builder().build()

    // Serialization
    fun runJackson(payload: Payload): String {
        return com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(payload)
    }

    fun runKotlinx(payload: Payload): String {
        return Json.encodeToString(Payload.serializer(), payload)
    }

    fun runGson(payload: Payload): String {
        return gson.toJson(payload)
    }

    // DB Operations with Circuit Breaker
    @CircuitBreaker(name = "dbService", fallbackMethod = "dbFallback")
    fun runDbQuery(id: Int): Map<String, Any> {
        val db = jdbcTemplate
        if (db == null) {
            return mapOf("status" to "mock", "id" to id)
        }
        return db.queryForMap("SELECT * FROM items WHERE id = ?", id)
    }

    fun dbFallback(id: Int, t: Throwable): Map<String, Any> {
        return mapOf("status" to "fallback", "id" to id, "error" to (t.message ?: "unknown"))
    }

    // Cache Operations
    fun getCaffeine(key: String): String {
        return caffeineCache.get(key) { "caffeine-value-for-$key" }
    }

    fun getRedis(key: String): String {
        val redis = redisTemplate
        if (redis == null) {
            return "redis-mock-value-for-$key"
        }
        var valOpt = redis.opsForValue().get(key)
        if (valOpt == null) {
            valOpt = "redis-value-for-$key"
            redis.opsForValue().set(key, valOpt, Duration.ofMinutes(10))
        }
        return valOpt
    }

    // HTTP Client Call (Calling a self endpoint to mock external HTTP backend)
    @CircuitBreaker(name = "httpService", fallbackMethod = "httpFallback")
    fun runOkHttpCall(): String {
        val request = Request.Builder()
            .url("http://localhost:8080/api/ping")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: "empty"
        }
    }

    @CircuitBreaker(name = "httpService", fallbackMethod = "httpFallback")
    fun runWebClientCall(): String {
        return webClient.get()
            .uri("http://localhost:8080/api/ping")
            .retrieve()
            .bodyToMono(String::class.java)
            .block(Duration.ofSeconds(1)) ?: "empty"
    }

    fun httpFallback(t: Throwable): String {
        return "fallback-response-due-to-${t.javaClass.simpleName}"
    }

    // Business Logic
    fun computeHeavyTask(iterations: Int): Int {
        var count = 0
        for (i in 1..iterations) {
            if (isPrime(i)) {
                count++
            }
        }
        return count
    }

    private fun isPrime(n: Int): Boolean {
        if (n <= 1) return false
        for (i in 2..kotlin.math.sqrt(n.toDouble()).toInt()) {
            if (n % i == 0) return false
        }
        return true
    }
}
