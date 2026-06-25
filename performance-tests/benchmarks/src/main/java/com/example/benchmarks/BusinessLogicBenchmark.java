package com.example.benchmarks;

import com.example.service.BenchmarkService;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class BusinessLogicBenchmark {

    @Param({"100", "1000"})
    public int size = 100;

    private final Random random = new Random();
    private BenchmarkService benchmarkService;
    private List<Integer> dataList;

    @Setup
    public void setup() {
        benchmarkService = new BenchmarkService(null, null);
        dataList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            dataList.add(random.nextInt(10000));
        }
    }

    @Benchmark
    public int computeHeavyTask() {
        return benchmarkService.computeHeavyTask(size);
    }

    @Benchmark
    public List<Integer> sortList() {
        List<Integer> list = new ArrayList<>(dataList);
        Collections.sort(list);
        return list;
    }

    @Benchmark
    public int binarySearch() {
        List<Integer> list = new ArrayList<>(dataList);
        Collections.sort(list);
        return Collections.binarySearch(list, 5000);
    }
}
