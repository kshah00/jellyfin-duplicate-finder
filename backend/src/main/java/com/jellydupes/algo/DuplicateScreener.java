package com.jellydupes.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bloom-filter-first exact duplicate screening.
 *
 * <p>For each key: ask the Bloom filter. A definite "no" means the key has not been seen, so the
 * item is unique so far and the HashMap is never consulted for membership. A "possibly present"
 * answer is resolved by an exact lookup in the HashMap of stored records: either a confirmed
 * duplicate or a Bloom false positive.
 *
 * <p>Invariants: {@code screenedOut + possiblyPresent == offered} and
 * {@code possiblyPresent == confirmedDuplicates + falsePositives}.
 */
public final class DuplicateScreener<T> {

    public enum Outcome { SCREENED_OUT, CONFIRMED_DUPLICATE, FALSE_POSITIVE }

    private final BloomFilter bloom;
    private final Map<String, List<T>> store = new HashMap<>();
    private long screenedOut;
    private long possiblyPresent;
    private long confirmedDuplicates;
    private long falsePositives;

    public DuplicateScreener(BloomFilter bloom) {
        this.bloom = bloom;
    }

    public Outcome offer(String key, T item) {
        Outcome outcome;
        if (!bloom.mightContain(key)) {
            screenedOut++;
            List<T> items = new ArrayList<>(1);
            items.add(item);
            store.put(key, items);
            outcome = Outcome.SCREENED_OUT;
        } else {
            possiblyPresent++;
            List<T> existing = store.get(key);
            if (existing != null) {
                confirmedDuplicates++;
                existing.add(item);
                outcome = Outcome.CONFIRMED_DUPLICATE;
            } else {
                falsePositives++;
                List<T> items = new ArrayList<>(1);
                items.add(item);
                store.put(key, items);
                outcome = Outcome.FALSE_POSITIVE;
            }
        }
        bloom.add(key);
        return outcome;
    }

    /** All keys with their items (including singletons). */
    public Map<String, List<T>> groups() {
        return Collections.unmodifiableMap(store);
    }

    /** Only keys that occurred more than once. */
    public Map<String, List<T>> duplicateGroups() {
        Map<String, List<T>> dups = new LinkedHashMap<>();
        store.forEach((k, v) -> {
            if (v.size() > 1) {
                dups.put(k, v);
            }
        });
        return dups;
    }

    public BloomFilter bloom() {
        return bloom;
    }

    public long offered() {
        return screenedOut + possiblyPresent;
    }

    public long screenedOut() {
        return screenedOut;
    }

    public long possiblyPresent() {
        return possiblyPresent;
    }

    public long confirmedDuplicates() {
        return confirmedDuplicates;
    }

    public long falsePositives() {
        return falsePositives;
    }

    /** Observed false-positive rate among keys that really were new: FP / (screenedOut + FP). */
    public double observedFalsePositiveRate() {
        long negatives = screenedOut + falsePositives;
        return negatives == 0 ? 0.0 : (double) falsePositives / negatives;
    }
}
