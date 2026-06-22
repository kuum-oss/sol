package com.example.benchmarks;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CacheBenchmark {

    private final Cache<String, String> cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .build();

    // Счётчик в thread-scoped состоянии для гарантированного промаха на каждой итерации
    @State(Scope.Thread)
    public static class MissCounter {
        public final AtomicLong counter = new AtomicLong(0);
    }

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
    public String getCacheMissOrPut(MissCounter state) {
        // Уникальный ключ на каждую итерацию — гарантированный Cache Miss
        String missKey = "miss-key-" + state.counter.getAndIncrement();
        return cache.get(missKey, k -> "new-value-for-" + k);
    }
}
