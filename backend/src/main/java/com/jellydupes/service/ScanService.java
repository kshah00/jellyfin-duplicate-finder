package com.jellydupes.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Parses uploads, runs the detector and keeps the latest library searchable. */
@Service
public class ScanService {

    public static final String SAMPLE_RESOURCE = "sample-library.csv";

    private final CsvLibraryParser parser = new CsvLibraryParser();
    private final DuplicateDetector detector = new DuplicateDetector();
    private final AtomicReference<LibraryIndex> current = new AtomicReference<>();

    @PostConstruct
    void loadSample() throws IOException {
        current.set(new LibraryIndex("sample", parser.parse(sampleCsv())));
    }

    public record ScanOutcome(DuplicateDetector.Result result, AccuracyEvaluator.Accuracy accuracy, double parseMillis) {
    }

    public ScanOutcome scan(InputStream csv, String sourceName, double fpRate, int maxDistance, Long seed) throws IOException {
        long t0 = System.nanoTime();
        List<MediaItem> items = parser.parse(csv);
        double parseMillis = (System.nanoTime() - t0) / 1e6;
        DuplicateDetector.Result result = detector.detect(items, fpRate, maxDistance, seed);
        current.set(new LibraryIndex(sourceName, items));
        return new ScanOutcome(result, AccuracyEvaluator.evaluate(result), parseMillis);
    }

    public LibraryIndex currentIndex() {
        return current.get();
    }

    public String sampleCsv() throws IOException {
        try (InputStream in = new ClassPathResource(SAMPLE_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
