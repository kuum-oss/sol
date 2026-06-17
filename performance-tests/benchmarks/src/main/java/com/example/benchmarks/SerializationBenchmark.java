package com.example.benchmarks;

import com.example.service.Payload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.google.gson.Gson;
import kotlinx.serialization.json.Json;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class SerializationBenchmark {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new KotlinModule.Builder().build());
    private final Gson gson = new Gson();
    
    @Param({"10", "100"})
    public int listSize = 10;

    private Payload payload;

    @Setup
    public void setup() {
        List<String> tags = new ArrayList<>();
        for (int i = 1; i <= listSize; i++) {
            tags.add("tag-" + i);
        }
        payload = new Payload("id-123", "Performance Test Payload", tags, 9.8);
    }

    @Benchmark
    public String jacksonSerialize() throws Exception {
        return mapper.writeValueAsString(payload);
    }

    @Benchmark
    public String gsonSerialize() {
        return gson.toJson(payload);
    }
}
