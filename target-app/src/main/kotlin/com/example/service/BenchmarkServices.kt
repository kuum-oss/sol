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
import java.time.Duration
import java.util.concurrent.TimeUnit

@Serializable
data class Payload(val id: String, val name: String, val tags: List<String>, val rating: Double)

@Service
class BenchmarkService(
    @Autowired(required = false) val jdbcTemplate: JdbcTemplate?,
    @Autowired(required = false) val redisTemplate: StringRedisTemplate?
) {
    // Схема и начальные данные управляются Flyway (db/migration/V1__create_items_table.sql)
    private val jacksonMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    private val gson = Gson()
    private val caffeineCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build<String, String>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()


    // Serialization
    fun runJackson(payload: Payload): String {
        return jacksonMapper.writeValueAsString(payload)
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
        return try {
            var valOpt = redis.opsForValue().get(key)
            if (valOpt == null) {
                valOpt = "redis-value-for-$key"
                redis.opsForValue().set(key, valOpt, Duration.ofMinutes(10))
            }
            valOpt
        } catch (e: Exception) {
            "redis-fallback-value-for-$key"
        }
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
        // Используем тот же OkHttpClient вместо блокирующего WebClient.block(),
        // чтобы не исчерпывать пул потоков реактивного планировщика в синхронном MVC-стеке
        val request = Request.Builder()
            .url("http://localhost:8080/api/ping")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            return response.body?.string() ?: "empty"
        }
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
