package com.jellydupes.algo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomFilterTest {

    private static List<String> keys(String prefix, int n) {
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(prefix + i);
        }
        return out;
    }

    @Test
    @DisplayName("every inserted key is reported as possibly present (no false negatives)")
    void noFalseNegatives() {
        BloomFilter bloom = new BloomFilter(10_000, 0.01, 1L, 2L);
        List<String> keys = keys("movie|title-", 10_000);
        keys.forEach(bloom::add);
        for (String k : keys) {
            assertTrue(bloom.mightContain(k), k);
        }
    }

    @Test
    @DisplayName("boundary: an empty filter contains nothing and has no bits set")
    void emptyFilter() {
        BloomFilter bloom = new BloomFilter(100, 0.01, 7L, 9L);
        assertEquals(0, bloom.setBitCount());
        assertFalse(bloom.mightContain(""));
        for (String k : keys("x", 1_000)) {
            assertFalse(bloom.mightContain(k));
        }
    }

    @Test
    @DisplayName("m and k follow m = -(n ln p)/(ln 2)^2 and k = (m/n) ln 2")
    void sizingFormulas() {
        // n = 1000, p = 0.01  ->  m = 9585.06 -> 9586, k = 6.64 -> 7
        assertEquals(9586, BloomFilter.optimalBitCount(1_000, 0.01));
        assertEquals(7, BloomFilter.optimalHashCount(1_000, 9586));
        // n = 100000, p = 0.001 -> m = 1437758.76 -> 1437759, k = 9.97 -> 10
        assertEquals(1_437_759, BloomFilter.optimalBitCount(100_000, 0.001));
        assertEquals(10, BloomFilter.optimalHashCount(100_000, 1_437_759));
        BloomFilter bloom = new BloomFilter(1_000, 0.01, 1, 2);
        assertEquals(9586, bloom.bitSize());
        assertEquals(7, bloom.hashCount());
        assertEquals((9586 + 63) / 64 * 8, bloom.memoryBytes());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.1, 1.5, Double.NaN})
    @DisplayName("boundary: invalid false-positive rates are rejected")
    void invalidP(double p) {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, p, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.optimalBitCount(100, p));
    }

    @Test
    @DisplayName("boundary: n must be positive; a huge p still yields k >= 1")
    void invalidNAndMinimumK() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 0.01, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(-5, 0.01, 1, 2));
        BloomFilter loose = new BloomFilter(10, 0.99, 1, 2);
        assertTrue(loose.hashCount() >= 1);
        assertTrue(loose.bitSize() >= 1);
        BloomFilter single = new BloomFilter(1, 0.5, 1, 2);
        single.add("only");
        assertTrue(single.mightContain("only"));
    }

    @Test
    @DisplayName("measured false-positive rate on 100k keys is close to the theoretical rate")
    void measuredFppNearTheory() {
        int n = 100_000;
        double p = 0.01;
        BloomFilter bloom = new BloomFilter(n, p, 0x1234L, 0x5678L);
        for (String k : keys("in-", n)) {
            bloom.add(k);
        }
        int falsePositives = 0;
        int probes = 100_000;
        for (String k : keys("out-", probes)) {
            if (bloom.mightContain(k)) {
                falsePositives++;
            }
        }
        double measured = (double) falsePositives / probes;
        double theory = bloom.theoreticalFpp(n);
        assertTrue(Math.abs(theory - p) < 0.002, "theory " + theory);
        // allow ±25% relative error: ~1000 expected false positives, sd ~31
        assertTrue(measured > theory * 0.75 && measured < theory * 1.25,
                "measured " + measured + " vs theory " + theory);
    }

    @Test
    @DisplayName("duplicate inserts are idempotent")
    void duplicateInsertsIdempotent() {
        BloomFilter bloom = new BloomFilter(1_000, 0.01, 3, 4);
        List<String> keys = keys("k", 500);
        int changed = 0;
        for (String k : keys) {
            changed += bloom.add(k) ? 1 : 0;
        }
        long bitsAfterFirst = bloom.setBitCount();
        long insertions = bloom.insertionCount();
        for (int round = 0; round < 3; round++) {
            for (String k : keys) {
                assertFalse(bloom.add(k), "re-adding must not change any bit");
            }
        }
        assertEquals(bitsAfterFirst, bloom.setBitCount());
        assertEquals(insertions, bloom.insertionCount());
        assertTrue(changed >= 490, "almost every first insert should set a new bit");
        keys.forEach(k -> assertTrue(bloom.mightContain(k)));
    }

    @Test
    @DisplayName("different seeds give different bit patterns but never a false negative")
    void differentSeedsNoFalseNegatives() {
        Random rnd = new Random(99);
        List<String> keys = keys("title|", 2_000);
        long previousBits = -1;
        boolean patternsDiffer = false;
        for (int s = 0; s < 25; s++) {
            BloomFilter bloom = new BloomFilter(keys.size(), 0.05, rnd.nextLong(), rnd.nextLong());
            keys.forEach(bloom::add);
            for (String k : keys) {
                assertTrue(bloom.mightContain(k));
            }
            if (previousBits != -1 && previousBits != bloom.setBitCount()) {
                patternsDiffer = true;
            }
            previousBits = bloom.setBitCount();
        }
        assertTrue(patternsDiffer);
        // the no-arg-seed constructor picks random seeds per run
        BloomFilter a = new BloomFilter(10, 0.01);
        BloomFilter b = new BloomFilter(10, 0.01);
        assertFalse(a.seed1() == b.seed1() && a.seed2() == b.seed2());
    }

    @Test
    @DisplayName("a forced false positive is rejected by the exact HashMap check")
    void forcedFalsePositiveRejectedByExactCheck() {
        // Deliberately tiny filter so collisions are easy to find.
        BloomFilter bloom = new BloomFilter(4, 0.4, 11, 13);
        DuplicateScreener<String> screener = new DuplicateScreener<>(bloom);
        for (String k : keys("stored-", 4)) {
            assertTrue(screener.offer(k, k) != DuplicateScreener.Outcome.CONFIRMED_DUPLICATE);
        }
        long fpBefore = screener.falsePositives();
        String forced = null;
        for (int i = 0; i < 100_000 && forced == null; i++) {
            String candidate = "never-inserted-" + i;
            if (bloom.mightContain(candidate)) {
                forced = candidate;
            }
        }
        assertNotNull(forced, "expected to find a false positive in a saturated filter");
        assertEquals(DuplicateScreener.Outcome.FALSE_POSITIVE, screener.offer(forced, forced));
        assertEquals(fpBefore + 1, screener.falsePositives());
        assertEquals(0, screener.confirmedDuplicates(), "a false positive is never counted as a duplicate");
        assertEquals(1, screener.groups().get(forced).size(), "stored as a new unique record");
        // a real duplicate is still confirmed
        assertEquals(DuplicateScreener.Outcome.CONFIRMED_DUPLICATE, screener.offer("stored-0", "again"));
        assertEquals(1, screener.confirmedDuplicates());
        assertEquals(screener.offered(), screener.screenedOut() + screener.possiblyPresent());
        assertEquals(screener.possiblyPresent(), screener.confirmedDuplicates() + screener.falsePositives());
    }
}
