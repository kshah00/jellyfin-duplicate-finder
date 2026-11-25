package com.jellydupes.service;

import com.jellydupes.bench.LibraryGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateDetectorTest {

    private final CsvLibraryParser parser = new CsvLibraryParser();
    private final DuplicateDetector detector = new DuplicateDetector();

    private List<MediaItem> sample() throws IOException {
        try (InputStream in = new ClassPathResource("sample-library.csv").getInputStream()) {
            return parser.parse(in);
        }
    }

    private static Set<String> titlesOf(DuplicateDetector.DuplicateGroup g) {
        return g.items().stream().map(MediaItem::title).collect(Collectors.toSet());
    }

    private static DuplicateDetector.DuplicateGroup groupContaining(DuplicateDetector.Result r, String title) {
        return r.groups().stream().filter(g -> titlesOf(g).contains(title)).findFirst().orElse(null);
    }

    @Test
    void sampleGroupsMatchExpectations() throws IOException {
        DuplicateDetector.Result r = detector.detect(sample(), 0.01, 2, 7L);

        DuplicateDetector.DuplicateGroup matrix = groupContaining(r, "The Matrix");
        assertNotNull(matrix);
        assertEquals(Set.of("The Matrix", "The.Matrix.1999.1080p.BluRay.x264-SPARKS", "The Matrix (1999) [YTS.MX]",
                "The Matirx"), titlesOf(matrix));
        assertEquals(DuplicateDetector.Kind.NEAR, matrix.kind());
        assertEquals(48318382080L, matrix.keep().sizeBytes(), "the largest copy is kept");
        assertEquals(matrix.totalBytes() - 48318382080L, matrix.reclaimableBytes());

        // different films that look alike are not merged
        assertFalse(titlesOf(groupContaining(r, "Blade Runner 2049")).contains("Blade Runner"));
        assertEquals(null, groupContaining(r, "Toy Story 2"));
        assertEquals(null, groupContaining(r, "Up"));
        assertEquals(null, groupContaining(r, "Parasite"), "parasite/paradise are 2 edits but only 8 chars");
        assertEquals(Set.of("Dune", "Dune (2021) - 1080p WEB-DL"), titlesOf(groupContaining(r, "Dune (2021) - 1080p WEB-DL")));
        assertEquals(DuplicateDetector.Kind.EXACT, groupContaining(r, "Dune (2021) - 1080p WEB-DL").kind());

        // episodes only merge with the same season/episode
        assertEquals(2, groupContaining(r, "Braking Bad").items().size());
        assertTrue(r.reclaimableBytes() > 0);
        assertEquals(r.stats().offered(), r.items().size());
    }

    @Test
    void maxDistanceZeroFindsOnlyExactDuplicates() throws IOException {
        DuplicateDetector.Result r = detector.detect(sample(), 0.01, 0, 7L);
        assertTrue(r.groups().stream().allMatch(g -> g.kind() == DuplicateDetector.Kind.EXACT));
        assertEquals(null, groupContaining(r, "The Matirx"));
    }

    @Test
    void invalidParametersRejected() throws IOException {
        List<MediaItem> items = sample();
        assertThrows(ValidationException.class, () -> detector.detect(items, 0, 2, null));
        assertThrows(ValidationException.class, () -> detector.detect(items, 1, 2, null));
        assertThrows(ValidationException.class, () -> detector.detect(items, 0.01, -1, null));
        assertThrows(ValidationException.class, () -> detector.detect(List.of(), 0.01, 2, null));
    }

    @Test
    void effectiveDistanceScalesWithLength() {
        assertEquals(0, DuplicateDetector.effectiveDistance(4, 2));
        assertEquals(1, DuplicateDetector.effectiveDistance(5, 2));
        assertEquals(1, DuplicateDetector.effectiveDistance(8, 2));
        assertEquals(2, DuplicateDetector.effectiveDistance(9, 2));
        assertEquals(1, DuplicateDetector.effectiveDistance(30, 1));
        assertEquals("2 3", DuplicateDetector.digits("toy story 2 part 3"));
    }

    @Test
    void plantedDuplicatesAreFoundWithHighPrecisionAndRecall() {
        LibraryGenerator.Dataset data = new LibraryGenerator().generate(1_000, 42L);
        List<MediaItem> items = parser.parse(data.toCsv());
        DuplicateDetector.Result r = detector.detect(items, 0.01, 2, 42L);
        AccuracyEvaluator.Accuracy a = AccuracyEvaluator.evaluate(r);
        assertNotNull(a);
        assertEquals(1.0, a.exactRecall(), 1e-9, "exact variants normalize to the same key");
        assertTrue(a.nearRecall() > 0.95, "near recall " + a.nearRecall());
        assertTrue(a.precision() > 0.95, "precision " + a.precision());
    }

    @Test
    void generatorIsDeterministic() {
        LibraryGenerator g = new LibraryGenerator();
        assertEquals(g.generate(500, 7L).toCsv(), g.generate(500, 7L).toCsv());
        assertFalse(g.generate(500, 7L).toCsv().equals(g.generate(500, 8L).toCsv()));
        assertEquals(500, g.generate(500, 7L).rows().size());
    }
}
