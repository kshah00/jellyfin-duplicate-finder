package com.jellydupes.algo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizerTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "The Matrix|the matrix",
            "The.Matrix.1999.1080p.BluRay.x264-SPARKS|the matrix",
            "The Matrix (1999) [YTS.MX]|the matrix",
            "Mad Max: Fury Road!|mad max fury road",
            "Amélie|amelie",
            "Charlotte's Web|charlottes web",
            "Fast & Furious|fast and furious",
            "Dune - 1080p WEB-DL|dune",
            "Stranger.Things.S01E01.1080p.WEBRip.x265-RARBG|stranger things",
            "The Hobbit (Extended Edition)|the hobbit",
            "Blade.Runner.2049.2017.2160p.UHD.BluRay.x265-TERMiNAL|blade runner 2049",
            "Movie {RARBG} 720p|movie",
    })
    void normalizesTitles(String raw, String expected) {
        assertEquals(expected, Normalizer.normalizeTitle(raw));
    }

    @Test
    @DisplayName("years are kept separately and title numbers are not mistaken for years")
    void yearHandling() {
        assertEquals(new Normalizer.NormalizedTitle("the matrix", 1999), Normalizer.normalize("The Matrix (1999)", null));
        assertEquals(new Normalizer.NormalizedTitle("the matrix", 1999), Normalizer.normalize("The.Matrix.1999.720p", null));
        assertEquals(new Normalizer.NormalizedTitle("blade runner 2049", 2017), Normalizer.normalize("Blade Runner 2049", 2017));
        assertEquals(new Normalizer.NormalizedTitle("blade runner 2049", null), Normalizer.normalize("Blade Runner 2049", null));
        assertEquals(new Normalizer.NormalizedTitle("1917", 2019), Normalizer.normalize("1917", 2019));
        assertEquals(new Normalizer.NormalizedTitle("2012", 2009), Normalizer.normalize("2012 (2009)", null));
    }

    @Test
    @DisplayName("boundary: null, blank and tag-only titles normalize to empty")
    void emptyInputs() {
        assertEquals("", Normalizer.normalizeTitle(null));
        assertEquals("", Normalizer.normalizeTitle("   "));
        assertEquals("", Normalizer.normalizeTitle("!!!"));
        assertEquals("", Normalizer.normalizeTitle("1080p BluRay x264"));
        assertEquals("x", Normalizer.normalizeTitle("X"));
    }

    @Test
    @DisplayName("duplicate keys include type, year and episode")
    void keys() {
        assertEquals("movie|dune|2021", Normalizer.key("movie", "dune", 2021, null, null));
        assertEquals("movie|dune|", Normalizer.key("movie", "dune", null, null, null));
        assertEquals("episode|breaking bad|2008|s1e3", Normalizer.key("episode", "breaking bad", 2008, 1, 3));
    }
}
