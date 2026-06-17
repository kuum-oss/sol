package com.example.benchmarks;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CacheBenchmark {

    private final Cache<String, String> cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .build();

    @Setup
    public void setup() {
        for (int i = 1; i <= 1000; i++) {
            cache.put("key-" + i, "val-" + i);
        }
    }

    @Benchmark
    public String getCacheHit() {
        return cache.getIfPresent("key-500");
    }

    @Benchmark
    public String getCacheMissOrPut() {
        return cache.get("key-miss", k -> "new-value");
    }
}
