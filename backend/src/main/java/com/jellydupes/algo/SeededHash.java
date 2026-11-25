package com.jellydupes.algo;

/**
 * Two independent, hand-written, seeded 64-bit string hash functions used by {@link BloomFilter}
 * for double hashing. Both finish with the same avalanche finalizer so every input bit affects
 * every output bit; the seeds make each filter instance use a different hash family.
 */
public final class SeededHash {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private SeededHash() {
    }

    /**
     * FNV-1a over the UTF-16 code units (low byte, then high byte), seeded through the offset basis.
     */
    public static long fnv1a64(CharSequence s, long seed) {
        long h = FNV_OFFSET ^ fmix64(seed);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            h ^= (c & 0xff);
            h *= FNV_PRIME;
            h ^= (c >>> 8);
            h *= FNV_PRIME;
        }
        return fmix64(h);
    }

    /**
     * Block hash in the style of MurmurHash3: four chars (64 bits) per block, multiply-rotate-multiply
     * mixing per block, then the length and the finalizer.
     */
    public static long blockMix64(CharSequence s, long seed) {
        int n = s.length();
        long h = seed ^ (n * GOLDEN);
        int i = 0;
        while (i + 4 <= n) {
            long k = s.charAt(i)
                    | ((long) s.charAt(i + 1) << 16)
                    | ((long) s.charAt(i + 2) << 32)
                    | ((long) s.charAt(i + 3) << 48);
            h ^= mixBlock(k);
            h = Long.rotateLeft(h, 27) * 5 + 0x52dce729L;
            i += 4;
        }
        long tail = 0;
        for (int shift = 0; i < n; i++, shift += 16) {
            tail |= ((long) s.charAt(i)) << shift;
        }
        h ^= mixBlock(tail);
        h ^= n;
        return fmix64(h);
    }

    private static long mixBlock(long k) {
        k *= C1;
        k = Long.rotateLeft(k, 31);
        k *= C2;
        return k;
    }

    /** 64-bit avalanche finalizer (Murmur3 fmix64 constants). */
    static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }
}
