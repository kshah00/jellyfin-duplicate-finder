package com.jellydupes.api;

import com.jellydupes.service.ScanService;
import com.jellydupes.service.ValidationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    /**
     * Scans an uploaded library export. Read-only: files on disk are never touched.
     *
     * @param fpRate      Bloom filter target false-positive rate, 0 &lt; p &lt; 1 (default 0.01)
     * @param maxDistance maximum Levenshtein distance for near duplicates, 0-5 (default 2)
     * @param seed        optional Bloom hash seed for reproducible runs; random when omitted
     */
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScanResponse scan(@RequestPart(value = "file", required = false) MultipartFile file,
                             @RequestParam(defaultValue = "0.01") double fpRate,
                             @RequestParam(defaultValue = "2") int maxDistance,
                             @RequestParam(required = false) Long seed) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Upload a non-empty CSV file in the 'file' form field.");
        }
        if (Double.isNaN(fpRate) || fpRate <= 0 || fpRate >= 1) {
            throw new ValidationException("fpRate must be between 0 and 1 (exclusive), got " + fpRate);
        }
        if (maxDistance < 0 || maxDistance > 5) {
            throw new ValidationException("maxDistance must be between 0 and 5, got " + maxDistance);
        }
        String name = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        try (InputStream in = file.getInputStream()) {
            return ScanResponse.from(scanService.scan(in, name, fpRate, maxDistance, seed), name, fpRate, maxDistance);
        }
    }
}
