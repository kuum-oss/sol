package com.example.benchmarks;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HttpClientBenchmark {

    private MockWebServer server;
    private OkHttpClient client;
    private Request request;

    @Setup
    public void setup() throws IOException {
        server = new MockWebServer();
        server.enqueue(new MockResponse().setBody("pong"));
        server.start();

        client = new OkHttpClient.Builder()
                .connectTimeout(500, TimeUnit.MILLISECONDS)
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .build();

        request = new Request.Builder()
                .url(server.url("/api/ping"))
                .build();
    }

    @TearDown
    public void teardown() throws IOException {
        server.shutdown();
    }

    @Benchmark
    public String okhttpClientRequest() throws IOException {
        server.enqueue(new MockResponse().setBody("pong"));
        try (Response response = client.newCall(request).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }
}
