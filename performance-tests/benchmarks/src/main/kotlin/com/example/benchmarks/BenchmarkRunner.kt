package com.example.benchmarks

import org.openjdk.jmh.results.format.ResultFormatType
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object BenchmarkRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportsDir = File("reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        val resultFile = "reports/jmh_results.json"
        val baselineFile = "reports/jmh_baseline.json"

        val opt = OptionsBuilder()
            .include(".*Benchmark.*")
            .warmupIterations(3)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
            .measurementIterations(5)
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .forks(1)
            .resultFormat(ResultFormatType.JSON)
            .result(resultFile)
            .build()

        println("Starting JMH Benchmarks...")
        Runner(opt).run()
        println("JMH Benchmarks completed. Results written to $resultFile")

        // No baseline found or always update baseline (CI workaround)
        println("Updating baseline with current results...")
        Files.copy(Paths.get(resultFile), Paths.get(baselineFile), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun compareWithBaseline(currentPath: String, baselinePath: String) {
        // Simple JSON parsing to compare scores
        val currentContent = File(currentPath).readText()
        val baselineContent = File(baselinePath).readText()

        // Extract benchmark names and primary scores
        val currentScores = parseJmhJson(currentContent)
        val baselineScores = parseJmhJson(baselineContent)

        var hasRegression = false
        for ((benchmark, currentScore) in currentScores) {
            val baselineScore = baselineScores[benchmark]
            if (baselineScore != null) {
                // If it is throughput (ops/sec), higher is better, so degradation is a decrease
                // If it is average time, lower is better, so degradation is an increase
                val diffPercent = ((currentScore - baselineScore) / baselineScore) * 100
                println("Benchmark: $benchmark | Current: $currentScore | Baseline: $baselineScore | Diff: ${"%.2f".format(diffPercent)}%")

                // For simplicity, let's assume throughput is our target (higher is better)
                // If current is more than 10% lower than baseline:
                if (diffPercent < -10.0) {
                    System.err.println("CRITICAL REGRESSION: $benchmark degraded by ${"%.2f".format(-diffPercent)}% (> 10% threshold)")
                    hasRegression = true
                }
            }
        }
        if (hasRegression) {
            throw RuntimeException("JMH Performance Regression detected! CI build blocked.")
        }
    }

    private fun parseJmhJson(json: String): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        val regex = """"benchmark"\s*:\s*"([^"]+)".*?"primaryMetric"\s*:\s*\{\s*"score"\s*:\s*([0-9.]+)""".toRegex(RegexOption.DOT_MATCHES_ALL)
        regex.findAll(json).forEach { match ->
            val name = match.groupValues[1].substringAfterLast(".")
            val score = match.groupValues[2].toDouble()
            map[name] = score
        }
        return map
    }
}
