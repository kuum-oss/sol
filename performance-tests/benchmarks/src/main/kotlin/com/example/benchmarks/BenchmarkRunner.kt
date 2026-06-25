package com.example.benchmarks

import org.openjdk.jmh.results.format.ResultFormatType
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object BenchmarkRunner {
    private const val DEFAULT_REGRESSION_THRESHOLD_PERCENT = 10.0

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

        if (File(baselineFile).exists()) {
            compareWithBaseline(resultFile, baselineFile)
        } else {
            println("No baseline found. Creating baseline at $baselineFile")
            Files.copy(Paths.get(resultFile), Paths.get(baselineFile), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun compareWithBaseline(currentPath: String, baselinePath: String) {
        val thresholdPercent = System.getProperty("jmh.regression.threshold.percent")
            ?.toDoubleOrNull()
            ?: DEFAULT_REGRESSION_THRESHOLD_PERCENT

        // Simple JSON parsing to compare scores
        val currentContent = File(currentPath).readText()
        val baselineContent = File(baselinePath).readText()

        // Extract benchmark names and primary scores
        val currentScores = parseJmhJson(currentContent)
        val baselineScores = parseJmhJson(baselineContent)

        var hasRegression = false
        for ((benchmark, currentResult) in currentScores) {
            val baselineResult = baselineScores[benchmark]
            if (baselineResult != null && baselineResult.score != 0.0) {
                val diffPercent = ((currentResult.score - baselineResult.score) / baselineResult.score) * 100
                val degraded = when (currentResult.mode) {
                    "thrpt" -> diffPercent < -thresholdPercent
                    "avgt", "sample", "ss" -> diffPercent > thresholdPercent
                    else -> false
                }

                println(
                    "Benchmark: $benchmark | Mode: ${currentResult.mode} | Current: ${currentResult.score} | " +
                        "Baseline: ${baselineResult.score} | Diff: ${"%.2f".format(diffPercent)}%"
                )

                if (degraded) {
                    System.err.println("CRITICAL REGRESSION: $benchmark degraded by more than $thresholdPercent%")
                    hasRegression = true
                }
            }
        }
        if (hasRegression) {
            throw RuntimeException("JMH Performance Regression detected! CI build blocked.")
        }
    }

    private fun parseJmhJson(json: String): Map<String, BenchmarkResult> {
        val map = mutableMapOf<String, BenchmarkResult>()
        val regex = """"benchmark"\s*:\s*"([^"]+)".*?"mode"\s*:\s*"([^"]+)".*?"primaryMetric"\s*:\s*\{\s*"score"\s*:\s*([0-9.Ee+-]+)""".toRegex(RegexOption.DOT_MATCHES_ALL)
        regex.findAll(json).forEach { match ->
            val name = match.groupValues[1].substringAfterLast(".")
            val mode = match.groupValues[2]
            val score = match.groupValues[3].toDouble()
            map[name] = BenchmarkResult(mode, score)
        }
        return map
    }

    private data class BenchmarkResult(
        val mode: String,
        val score: Double
    )
}
