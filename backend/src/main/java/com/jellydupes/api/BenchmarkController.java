package com.jellydupes.api;

import com.jellydupes.bench.BenchmarkHarness;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BenchmarkController {

    /** All fields optional; omitted ones use {@link BenchmarkHarness.Config#defaults()}. */
    public record BenchmarkRequest(List<Integer> sizes, Double fpRate, Integer maxDistance, Integer queries,
                                   Integer warmupRuns, Integer measuredRuns, Long seed) {
    }

    private final BenchmarkHarness harness;

    public BenchmarkController(BenchmarkHarness harness) {
        this.harness = harness;
    }

    @PostMapping("/benchmark")
    public BenchmarkHarness.Report benchmark(@RequestBody(required = false) BenchmarkRequest req) {
        BenchmarkHarness.Config d = BenchmarkHarness.Config.defaults();
        BenchmarkHarness.Config cfg = req == null ? d : new BenchmarkHarness.Config(
                req.sizes() != null ? req.sizes() : d.sizes(),
                req.fpRate() != null ? req.fpRate() : d.fpRate(),
                req.maxDistance() != null ? req.maxDistance() : d.maxDistance(),
                req.queries() != null ? req.queries() : d.queries(),
                req.warmupRuns() != null ? req.warmupRuns() : d.warmupRuns(),
                req.measuredRuns() != null ? req.measuredRuns() : d.measuredRuns(),
                req.seed() != null ? req.seed() : d.seed());
        synchronized (this) { // one benchmark at a time so runs don't skew each other
            return harness.run(cfg);
        }
    }
}
