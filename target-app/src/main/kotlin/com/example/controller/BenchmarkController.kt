package com.example.controller

import com.example.service.BenchmarkService
import com.example.service.Payload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class BenchmarkController(@Autowired val benchmarkService: BenchmarkService) {

    @GetMapping("/ping")
    fun ping(): String = "pong"

    @PostMapping("/serialize/jackson")
    fun jackson(@RequestBody payload: Payload): String {
        return benchmarkService.runJackson(payload)
    }

    @PostMapping("/serialize/kotlinx")
    fun kotlinx(@RequestBody payload: Payload): String {
        return benchmarkService.runKotlinx(payload)
    }

    @PostMapping("/serialize/gson")
    fun gson(@RequestBody payload: Payload): String {
        return benchmarkService.runGson(payload)
    }

    @GetMapping("/db/query")
    fun dbQuery(@RequestParam(defaultValue = "1") id: Int): Map<String, Any> {
        return benchmarkService.runDbQuery(id)
    }

    @GetMapping("/cache/caffeine")
    fun caffeine(@RequestParam key: String): String {
        return benchmarkService.getCaffeine(key)
    }

    @GetMapping("/cache/redis")
    fun redis(@RequestParam key: String): String {
        return benchmarkService.getRedis(key)
    }

    @GetMapping("/http/okhttp")
    fun okhttp(): String {
        return benchmarkService.runOkHttpCall()
    }

    @GetMapping("/http/webclient")
    fun webclient(): String {
        return benchmarkService.runWebClientCall()
    }

    @GetMapping("/algo/cpu")
    fun cpu(@RequestParam(defaultValue = "1000") iterations: Int): Int {
        return benchmarkService.computeHeavyTask(iterations)
    }
}
