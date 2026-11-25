package com.jellydupes.algo;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevenshteinAndHashTest {

    @Test
    void levenshteinKnownValues() {
        assertEquals(0, Levenshtein.distance("", ""));
        assertEquals(3, Levenshtein.distance("", "abc"));
        assertEquals(3, Levenshtein.distance("kitten", "sitting"));
        assertEquals(2, Levenshtein.distance("inception", "inceptoin"));
        assertEquals(2, Levenshtein.distance("matrix", "matirx"), "a swap costs two edits");
        assertEquals(Levenshtein.distance("flaw", "lawn"), Levenshtein.distance("lawn", "flaw"));
    }

    @Test
    void hashesAreDeterministicAndSeedSensitive() {
        assertEquals(SeededHash.fnv1a64("dune", 1), SeededHash.fnv1a64("dune", 1));
        assertEquals(SeededHash.blockMix64("dune", 1), SeededHash.blockMix64("dune", 1));
        assertNotEquals(SeededHash.fnv1a64("dune", 1), SeededHash.fnv1a64("dune", 2));
        assertNotEquals(SeededHash.blockMix64("dune", 1), SeededHash.blockMix64("dune", 2));
        assertNotEquals(SeededHash.fnv1a64("dune", 1), SeededHash.blockMix64("dune", 1));
        assertNotEquals(SeededHash.blockMix64("", 1), SeededHash.blockMix64("\0", 1), "length is mixed in");
    }

    @Test
    void hashesSpreadSimilarKeys() {
        Set<Long> low16a = new HashSet<>();
        Set<Long> low16b = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            low16a.add(SeededHash.fnv1a64("movie|title " + i, 42) & 0xffff);
            low16b.add(SeededHash.blockMix64("movie|title " + i, 42) & 0xffff);
        }
        // 1000 keys into 65536 buckets: expect ~992 distinct values if well mixed
        assertTrue(low16a.size() > 970, "fnv spread " + low16a.size());
        assertTrue(low16b.size() > 970, "block spread " + low16b.size());
    }
}
