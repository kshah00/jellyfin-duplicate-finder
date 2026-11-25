package com.jellydupes.bench;

import com.jellydupes.algo.BloomFilter;
import com.jellydupes.algo.BruteForceMatcher;
import com.jellydupes.algo.DuplicateScreener;
import com.jellydupes.algo.Trie;
import com.jellydupes.service.AccuracyEvaluator;
import com.jellydupes.service.CsvLibraryParser;
import com.jellydupes.service.DuplicateDetector;
import com.jellydupes.service.MediaItem;
import com.jellydupes.service.ValidationException;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Benchmarks on seeded synthetic libraries:
 * <ol>
 *   <li>Exact duplicates: Bloom filter + HashMap confirmation vs a HashSet-only baseline.</li>
 *   <li>Fuzzy title search: trie with pruned Levenshtein rows vs brute-force Levenshtein over all titles.</li>
 *   <li>End-to-end detection accuracy (pairwise precision / recall) against the planted labels.</li>
 * </ol>
 * Fixed seeds, warm-up runs, and the median of the measured runs. Both sides of each comparison are
 * checked to produce identical answers before timings are reported.
 */
@Service
public class BenchmarkHarness {

    public record Config(List<Integer> sizes, double fpRate, int maxDistance, int queries, int warmupRuns,
                         int measuredRuns, long seed) {

        public static Config defaults() {
            return new Config(List.of(1_000, 10_000), 0.01, 2, 200, 2, 5, 42L);
        }

        public Config validated() {
            if (sizes == null || sizes.isEmpty()) {
                throw new ValidationException("sizes must contain at least one dataset size.");
            }
            for (int s : sizes) {
                if (s < 10 || s > 200_000) {
                    throw new ValidationException("Each size must be between 10 and 200000, got " + s);
                }
            }
            if (Double.isNaN(fpRate) || fpRate <= 0 || fpRate >= 1) {
                throw new ValidationException("fpRate must be between 0 and 1 (exclusive), got " + fpRate);
            }
            if (maxDistance < 0 || maxDistance > 3) {
                throw new ValidationException("maxDistance must be between 0 and 3, got " + maxDistance);
            }
            if (queries < 1 || queries > 5_000) {
                throw new ValidationException("queries must be between 1 and 5000, got " + queries);
            }
            if (warmupRuns < 0 || warmupRuns > 10 || measuredRuns < 1 || measuredRuns > 25) {
                throw new ValidationException("warmupRuns must be 0-10 and measuredRuns 1-25.");
            }
            return this;
        }
    }

    public record Timing(double medianMs, double minMs, double maxMs, List<Double> runsMs) {
    }

    public record DedupeResult(Timing bloomPipeline, Timing hashSetBaseline, long duplicatesBloom,
                               long duplicatesHashSet, boolean answersAgree, long bloomBits, int bloomHashes,
                               long bloomBytes, double theoreticalFpp, long screenedOut, long possiblyPresent,
                               long confirmedDuplicates, long falsePositives, double observedFpRate) {
    }

    public record FuzzyResult(int queries, int indexedTitles, int trieNodes, double trieBuildMs, Timing trie,
                              Timing bruteForce, long matchesTrie, long matchesBruteForce, boolean answersAgree,
                              double speedup) {
    }

    public record AccuracyResult(double scanMs, int groups, AccuracyEvaluator.Accuracy accuracy) {
    }

    public record SizeResult(int records, int plantedExact, int plantedNear, DedupeResult dedupe, FuzzyResult fuzzy,
                             AccuracyResult accuracy) {
    }

    public record Machine(String os, String arch, String cpu, int availableProcessors, long maxHeapMb,
                          String javaVersion, String javaVendor, String jvm) {
    }

    public record Report(Instant startedAt, double totalSeconds, Machine machine, Config config, List<SizeResult> results) {
    }

    private final LibraryGenerator generator = new LibraryGenerator();
    private final CsvLibraryParser parser = new CsvLibraryParser();
    private final DuplicateDetector detector = new DuplicateDetector();

    public Report run(Config config) {
        Config cfg = config.validated();
        Instant start = Instant.now();
        long t0 = System.nanoTime();
        List<SizeResult> results = new ArrayList<>();
        for (int size : cfg.sizes()) {
            results.add(runSize(size, cfg));
        }
        return new Report(start, (System.nanoTime() - t0) / 1e9, machine(), cfg, results);
    }

    private SizeResult runSize(int size, Config cfg) {
        LibraryGenerator.Dataset data = generator.generate(size, cfg.seed() + size);
        List<MediaItem> items = parser.parse(data.toCsv());
        List<String> keys = new ArrayList<>(items.size());
        for (MediaItem m : items) {
            keys.add(m.key());
        }
        return new SizeResult(items.size(), data.exactVariants(), data.nearVariants(),
                dedupe(keys, cfg), fuzzy(items, cfg), accuracy(items, cfg));
    }

    // ---- 1. Bloom + exact check vs HashSet -------------------------------------------------------

    private DedupeResult dedupe(List<String> keys, Config cfg) {
        long s1 = cfg.seed() * 31 + 1;
        long s2 = cfg.seed() * 17 + 3;
        LongSupplier bloomRun = () -> {
            DuplicateScreener<String> screener = new DuplicateScreener<>(new BloomFilter(keys.size(), cfg.fpRate(), s1, s2));
            for (String k : keys) {
                screener.offer(k, k);
            }
            return screener.confirmedDuplicates();
        };
        LongSupplier hashSetRun = () -> {
            Set<String> seen = new HashSet<>();
            long dups = 0;
            for (String k : keys) {
                if (!seen.add(k)) {
                    dups++;
                }
            }
            return dups;
        };
        Timing bloomTiming = time(bloomRun, cfg);
        Timing hashTiming = time(hashSetRun, cfg);

        BloomFilter bloom = new BloomFilter(keys.size(), cfg.fpRate(), s1, s2);
        DuplicateScreener<String> screener = new DuplicateScreener<>(bloom);
        for (String k : keys) {
            screener.offer(k, k);
        }
        long hashDups = hashSetRun.getAsLong();
        return new DedupeResult(bloomTiming, hashTiming, screener.confirmedDuplicates(), hashDups,
                screener.confirmedDuplicates() == hashDups, bloom.bitSize(), bloom.hashCount(), bloom.memoryBytes(),
                bloom.theoreticalFpp(), screener.screenedOut(), screener.possiblyPresent(),
                screener.confirmedDuplicates(), screener.falsePositives(), screener.observedFalsePositiveRate());
    }

    // ---- 2. Trie fuzzy search vs brute-force Levenshtein -----------------------------------------

    private FuzzyResult fuzzy(List<MediaItem> items, Config cfg) {
        Set<String> distinct = new LinkedHashSet<>();
        for (MediaItem m : items) {
            distinct.add(m.normalizedTitle());
        }
        long b0 = System.nanoTime();
        Trie<String> trie = Trie.identity();
        for (String t : distinct) {
            trie.insert(t, t);
        }
        double buildMs = (System.nanoTime() - b0) / 1e6;
        BruteForceMatcher brute = new BruteForceMatcher(distinct);
        List<String> queries = queries(new ArrayList<>(distinct), cfg);
        int d = cfg.maxDistance();

        LongSupplier trieRun = () -> {
            long n = 0;
            for (String q : queries) {
                n += trie.fuzzySearch(q, d).size();
            }
            return n;
        };
        LongSupplier bruteRun = () -> {
            long n = 0;
            for (String q : queries) {
                n += brute.search(q, d).size();
            }
            return n;
        };
        Timing trieTiming = time(trieRun, cfg);
        Timing bruteTiming = time(bruteRun, cfg);

        boolean agree = true;
        for (String q : queries) {
            List<String> a = trie.fuzzySearch(q, d).stream().map(m -> m.word() + ":" + m.distance()).toList();
            List<String> b = brute.search(q, d).stream().map(m -> m.word() + ":" + m.distance()).toList();
            if (!a.equals(b)) {
                agree = false;
                break;
            }
        }
        return new FuzzyResult(queries.size(), trie.size(), trie.nodeCount(), buildMs, trieTiming, bruteTiming,
                trieRun.getAsLong(), bruteRun.getAsLong(), agree, bruteTiming.medianMs() / Math.max(1e-9, trieTiming.medianMs()));
    }

    /** Half the queries are exact titles, half have one random letter edit. Seeded. */
    private static List<String> queries(List<String> titles, Config cfg) {
        Random rnd = new Random(cfg.seed() ^ titles.size());
        List<String> out = new ArrayList<>(cfg.queries());
        for (int i = 0; i < cfg.queries(); i++) {
            String t = titles.get(rnd.nextInt(titles.size()));
            if (i % 2 == 1 && t.length() > 1) {
                int pos = rnd.nextInt(t.length());
                t = t.substring(0, pos) + (char) ('a' + rnd.nextInt(26)) + t.substring(pos + 1);
            }
            out.add(t);
        }
        return out;
    }

    // ---- 3. Accuracy ------------------------------------------------------------------------------

    private AccuracyResult accuracy(List<MediaItem> items, Config cfg) {
        long t0 = System.nanoTime();
        DuplicateDetector.Result result = detector.detect(items, cfg.fpRate(), cfg.maxDistance(), cfg.seed());
        double ms = (System.nanoTime() - t0) / 1e6;
        return new AccuracyResult(ms, result.groups().size(), AccuracyEvaluator.evaluate(result));
    }

    // ---- timing helpers ---------------------------------------------------------------------------

    static volatile long sink;

    private static Timing time(LongSupplier task, Config cfg) {
        for (int i = 0; i < cfg.warmupRuns(); i++) {
            sink += task.getAsLong();
        }
        double[] runs = new double[cfg.measuredRuns()];
        for (int i = 0; i < runs.length; i++) {
            long t0 = System.nanoTime();
            sink += task.getAsLong();
            runs[i] = (System.nanoTime() - t0) / 1e6;
        }
        List<Double> asList = Arrays.stream(runs).boxed().toList();
        double[] sorted = runs.clone();
        Arrays.sort(sorted);
        return new Timing(median(sorted), sorted[0], sorted[sorted.length - 1], asList);
    }

    static double median(double[] sorted) {
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    public static Machine machine() {
        Runtime rt = Runtime.getRuntime();
        return new Machine(
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                System.getProperty("os.arch"),
                cpuName(),
                rt.availableProcessors(),
                rt.maxMemory() / (1024 * 1024),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
    }

    private static String cpuName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] cmd = os.contains("mac") ? new String[]{"sysctl", "-n", "machdep.cpu.brand_string"}
                : os.contains("linux") ? new String[]{"sh", "-c", "grep -m1 'model name' /proc/cpuinfo | cut -d: -f2"}
                : null;
        if (cmd == null) {
            return "unknown";
        }
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                p.waitFor(2, TimeUnit.SECONDS);
                return line == null || line.isBlank() ? "unknown" : line.trim();
            }
        } catch (Exception e) {
            return "unknown";
        }
    }
}
