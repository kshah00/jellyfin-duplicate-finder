package com.jellydupes.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CLI benchmark: {@code mvn -q compile exec:java -Dexec.args="1000 10000 100000"}.
 * Prints a Markdown report and writes it to {@code benchmark-results.md}.
 */
public final class BenchmarkMain {

    public static void main(String[] args) throws IOException {
        List<Integer> sizes = new ArrayList<>();
        for (String a : args) {
            sizes.add(Integer.parseInt(a.replace("_", "")));
        }
        if (sizes.isEmpty()) {
            sizes = List.of(1_000, 10_000, 100_000);
        }
        BenchmarkHarness.Config cfg = new BenchmarkHarness.Config(sizes, 0.01, 2, 200, 2, 5, 42L);
        BenchmarkHarness.Report report = new BenchmarkHarness().run(cfg);
        String md = toMarkdown(report);
        System.out.println(md);
        Files.writeString(Path.of("benchmark-results.md"), md);
        System.out.println("Wrote benchmark-results.md");
    }

    public static String toMarkdown(BenchmarkHarness.Report r) {
        StringBuilder sb = new StringBuilder();
        BenchmarkHarness.Machine m = r.machine();
        BenchmarkHarness.Config c = r.config();
        sb.append("# Benchmark results\n\n");
        sb.append(String.format(Locale.ROOT, "- Run at: %s (%.1f s total)%n", r.startedAt(), r.totalSeconds()));
        sb.append(String.format("- Machine: %s, %s, %s, %d logical CPUs, max heap %d MB%n",
                m.cpu(), m.os(), m.arch(), m.availableProcessors(), m.maxHeapMb()));
        sb.append(String.format("- Java: %s (%s, %s)%n", m.javaVersion(), m.javaVendor(), m.jvm()));
        sb.append(String.format(Locale.ROOT, "- Config: seed %d, fpRate %.4f, max edit distance %d, %d fuzzy queries, %d warm-up + %d measured runs (median reported)%n%n",
                c.seed(), c.fpRate(), c.maxDistance(), c.queries(), c.warmupRuns(), c.measuredRuns()));

        sb.append("## Exact duplicates: Bloom filter + exact check vs HashSet only\n\n");
        sb.append("| records | Bloom+check median ms | HashSet median ms | duplicates (Bloom / HashSet) | bits m | hashes k | Bloom bytes | screened out | possibly present | false positives | observed FP rate | theoretical FPP |\n");
        sb.append("|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (BenchmarkHarness.SizeResult s : r.results()) {
            BenchmarkHarness.DedupeResult d = s.dedupe();
            sb.append(String.format(Locale.ROOT, "| %,d | %.3f | %.3f | %d / %d %s | %,d | %d | %,d | %,d | %,d | %,d | %.4f | %.4f |%n",
                    s.records(), d.bloomPipeline().medianMs(), d.hashSetBaseline().medianMs(), d.duplicatesBloom(),
                    d.duplicatesHashSet(), d.answersAgree() ? "(agree)" : "(MISMATCH)", d.bloomBits(), d.bloomHashes(),
                    d.bloomBytes(), d.screenedOut(), d.possiblyPresent(), d.falsePositives(), d.observedFpRate(),
                    d.theoreticalFpp()));
        }
        sb.append("\n## Fuzzy search: trie with pruning vs brute-force Levenshtein\n\n");
        sb.append("| records | distinct titles | trie nodes | build ms | queries | trie median ms | brute force median ms | speed-up | results agree |\n");
        sb.append("|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
        for (BenchmarkHarness.SizeResult s : r.results()) {
            BenchmarkHarness.FuzzyResult f = s.fuzzy();
            sb.append(String.format(Locale.ROOT, "| %,d | %,d | %,d | %.1f | %d | %.2f | %.2f | %.1fx | %s |%n",
                    s.records(), f.indexedTitles(), f.trieNodes(), f.trieBuildMs(), f.queries(), f.trie().medianMs(),
                    f.bruteForce().medianMs(), f.speedup(), f.answersAgree() ? "yes" : "NO"));
        }
        sb.append("\n## Detection accuracy on planted duplicates (pairwise)\n\n");
        sb.append("| records | planted exact | planted near | groups found | precision | recall | F1 | exact recall | near recall | full scan ms |\n");
        sb.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (BenchmarkHarness.SizeResult s : r.results()) {
            var a = s.accuracy().accuracy();
            sb.append(String.format(Locale.ROOT, "| %,d | %,d | %,d | %,d | %.4f | %.4f | %.4f | %.4f | %.4f | %.1f |%n",
                    s.records(), s.plantedExact(), s.plantedNear(), s.accuracy().groups(), a.precision(), a.recall(),
                    a.f1(), a.exactRecall(), a.nearRecall(), s.accuracy().scanMs()));
        }
        return sb.toString();
    }
}
