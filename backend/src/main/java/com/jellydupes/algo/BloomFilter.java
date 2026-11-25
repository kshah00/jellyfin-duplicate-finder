package com.jellydupes.algo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A classic Bloom filter over strings.
 *
 * <ul>
 *   <li>Bit array size: {@code m = ceil(-(n ln p) / (ln 2)^2)}</li>
 *   <li>Hash count: {@code k = round((m / n) ln 2)}, at least 1</li>
 *   <li>Index i of k: {@code h_i = h1 + i * h2 (mod m)} (Kirsch–Mitzenmacher double hashing), where
 *       h1 and h2 come from two independent hand-written seeded hashes in {@link SeededHash}.</li>
 * </ul>
 *
 * {@link #mightContain(String)} never returns a false negative; it returns a false positive with
 * probability roughly {@code (1 - e^(-kn/m))^k} once n keys are inserted.
 */
public final class BloomFilter {

    private static final double LN2 = Math.log(2);

    private final long[] words;
    private final long bitSize;
    private final int hashCount;
    private final int expectedInsertions;
    private final double targetFpp;
    private final long seed1;
    private final long seed2;
    private long setBits;
    private long insertions;

    /** Creates a filter with fresh random seeds (a different hash family on every run). */
    public BloomFilter(int expectedInsertions, double falsePositiveRate) {
        this(expectedInsertions, falsePositiveRate,
                ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong());
    }

    /** Creates a filter with explicit seeds, for reproducible runs and tests. */
    public BloomFilter(int expectedInsertions, double falsePositiveRate, long seed1, long seed2) {
        this.bitSize = optimalBitCount(expectedInsertions, falsePositiveRate);
        this.hashCount = optimalHashCount(expectedInsertions, bitSize);
        long wordCount = (bitSize + 63) / 64;
        if (wordCount > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Bloom filter too large: " + bitSize + " bits");
        }
        this.words = new long[(int) wordCount];
        this.expectedInsertions = expectedInsertions;
        this.targetFpp = falsePositiveRate;
        this.seed1 = seed1;
        this.seed2 = seed2;
    }

    /** m = -(n ln p) / (ln 2)^2, rounded up. */
    public static long optimalBitCount(long n, double p) {
        validate(n, p);
        double m = -(n * Math.log(p)) / (LN2 * LN2);
        return Math.max(1L, (long) Math.ceil(m));
    }

    /** k = (m / n) ln 2, rounded to the nearest integer and at least 1. */
    public static int optimalHashCount(long n, long m) {
        if (n <= 0) {
            throw new IllegalArgumentException("expected insertions must be > 0, got " + n);
        }
        if (m <= 0) {
            throw new IllegalArgumentException("bit count must be > 0, got " + m);
        }
        return Math.max(1, (int) Math.round((double) m / n * LN2));
    }

    private static void validate(long n, double p) {
        if (n <= 0) {
            throw new IllegalArgumentException("expected insertions must be > 0, got " + n);
        }
        if (Double.isNaN(p) || p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("false-positive rate must be in (0, 1), got " + p);
        }
    }

    /**
     * Adds a key. Returns true if at least one bit changed, i.e. the key was definitely not present
     * before. Adding the same key again is idempotent and returns false.
     */
    public boolean add(String key) {
        long h1 = SeededHash.fnv1a64(key, seed1);
        long h2 = SeededHash.blockMix64(key, seed2) | 1L; // odd step so probes don't collapse when h2 is even
        boolean changed = false;
        for (int i = 0; i < hashCount; i++) {
            long bit = index(h1, h2, i);
            int word = (int) (bit >>> 6);
            long mask = 1L << (bit & 63);
            if ((words[word] & mask) == 0) {
                words[word] |= mask;
                setBits++;
                changed = true;
            }
        }
        if (changed) {
            insertions++;
        }
        return changed;
    }

    /** False means definitely never added. True means possibly added. */
    public boolean mightContain(String key) {
        long h1 = SeededHash.fnv1a64(key, seed1);
        long h2 = SeededHash.blockMix64(key, seed2) | 1L;
        for (int i = 0; i < hashCount; i++) {
            long bit = index(h1, h2, i);
            if ((words[(int) (bit >>> 6)] & (1L << (bit & 63))) == 0) {
                return false;
            }
        }
        return true;
    }

    private long index(long h1, long h2, int i) {
        return Math.floorMod(h1 + i * h2, bitSize);
    }

    /** Theoretical false-positive probability after {@code n} insertions: (1 - e^(-kn/m))^k. */
    public double theoreticalFpp(long n) {
        return Math.pow(1 - Math.exp(-(double) hashCount * n / bitSize), hashCount);
    }

    /** Theoretical false-positive probability at the designed capacity. */
    public double theoreticalFpp() {
        return theoreticalFpp(expectedInsertions);
    }

    public long bitSize() {
        return bitSize;
    }

    public int hashCount() {
        return hashCount;
    }

    public int expectedInsertions() {
        return expectedInsertions;
    }

    public double targetFpp() {
        return targetFpp;
    }

    public long seed1() {
        return seed1;
    }

    public long seed2() {
        return seed2;
    }

    /** Number of bits currently set to 1. */
    public long setBitCount() {
        return setBits;
    }

    /** Number of add() calls that changed at least one bit. */
    public long insertionCount() {
        return insertions;
    }

    public long memoryBytes() {
        return (long) words.length * Long.BYTES;
    }
}
