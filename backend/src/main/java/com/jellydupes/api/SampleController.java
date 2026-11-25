package com.jellydupes.api;

import com.jellydupes.bench.LibraryGenerator;
import com.jellydupes.service.ScanService;
import com.jellydupes.service.ValidationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class SampleController {

    private static final MediaType CSV = new MediaType("text", "csv");

    private final ScanService scanService;
    private final LibraryGenerator generator = new LibraryGenerator();

    public SampleController(ScanService scanService) {
        this.scanService = scanService;
    }

    /** Small hand-written library export with known exact and near duplicates. */
    @GetMapping("/sample")
    public ResponseEntity<String> sample() throws IOException {
        return csv("sample-library.csv", scanService.sampleCsv());
    }

    /** Seeded synthetic library with planted, labeled duplicates (dup_group / dup_kind columns). */
    @GetMapping("/sample/generated")
    public ResponseEntity<String> generated(@RequestParam(defaultValue = "1000") int size,
                                            @RequestParam(defaultValue = "42") long seed) {
        if (size < 1 || size > 200_000) {
            throw new ValidationException("size must be between 1 and 200000, got " + size);
        }
        return csv("library-" + size + "-seed" + seed + ".csv", generator.generate(size, seed).toCsv());
    }

    private static ResponseEntity<String> csv(String name, String body) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(body);
    }
}
