package com.jellydupes.service;

import com.jellydupes.algo.BloomFilter;
import com.jellydupes.algo.DisjointSet;
import com.jellydupes.algo.DuplicateScreener;
import com.jellydupes.algo.Trie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the full duplicate scan:
 * <ol>
 *   <li>Exact duplicates: Bloom filter screening + HashMap confirmation ({@link DuplicateScreener}).</li>
 *   <li>Near duplicates: every distinct normalized title is fuzzy-searched in a {@link Trie} of all
 *       titles; compatible matches (same type, same season/episode, same or unknown year, same
 *       digits) are merged with union-find.</li>
 *   <li>Groups: one copy is kept (the largest file); the rest is reclaimable storage.</li>
 * </ol>
 * Read-only: nothing here touches the filesystem.
 */
public final class DuplicateDetector {

    public enum Kind { EXACT, NEAR }

    public record DuplicateGroup(int id, Kind kind, List<MediaItem> items, int keyCount,
                                 long totalBytes, long reclaimableBytes) {
        /** The copy that is kept: the largest file. */
        public MediaItem keep() {
            return items.get(0);
        }
    }

    public record ScreeningStats(long bits, int hashes, long bloomBytes, double targetFpp, double theoreticalFpp,
                                 long seed1, long seed2, long offered, long screenedOut, long possiblyPresent,
                                 long confirmedDuplicates, long falsePositives, double observedFpRate,
                                 int uniqueKeys, int distinctTitles, long nearPairsMerged,
                                 double bloomMillis, double nearMillis) {
    }

    public record Result(List<MediaItem> items, List<DuplicateGroup> groups, ScreeningStats stats) {
        public long totalBytes() {
            long t = 0;
            for (MediaItem i : items) {
                t += i.sizeBytes();
            }
            return t;
        }

        public long reclaimableBytes() {
            long t = 0;
            for (DuplicateGroup g : groups) {
                t += g.reclaimableBytes();
            }
            return t;
        }
    }

    public Result detect(List<MediaItem> items, double falsePositiveRate, int maxEditDistance, Long seed) {
        if (items.isEmpty()) {
            throw new ValidationException("No records to scan.");
        }
        if (Double.isNaN(falsePositiveRate) || falsePositiveRate <= 0 || falsePositiveRate >= 1) {
            throw new ValidationException("fpRate must be between 0 and 1 (exclusive), got " + falsePositiveRate);
        }
        if (maxEditDistance < 0 || maxEditDistance > 5) {
            throw new ValidationException("maxDistance must be between 0 and 5, got " + maxEditDistance);
        }

        // 1. Bloom filter + exact check
        long t0 = System.nanoTime();
        BloomFilter bloom = seed == null
                ? new BloomFilter(items.size(), falsePositiveRate)
                : new BloomFilter(items.size(), falsePositiveRate, seed, seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
        DuplicateScreener<MediaItem> screener = new DuplicateScreener<>(bloom);
        for (MediaItem item : items) {
            screener.offer(item.key(), item);
        }
        double bloomMillis = (System.nanoTime() - t0) / 1e6;

        // 2. Near duplicates over distinct keys
        long t1 = System.nanoTime();
        List<String> keys = new ArrayList<>(screener.groups().keySet());
        List<List<MediaItem>> keyItems = new ArrayList<>(keys.size());
        Trie<Integer> trie = Trie.identity();
        Map<String, List<Integer>> keysByTitle = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            List<MediaItem> group = screener.groups().get(keys.get(i));
            keyItems.add(group);
            String title = group.get(0).normalizedTitle();
            trie.insert(title, i);
            keysByTitle.computeIfAbsent(title, t -> new ArrayList<>()).add(i);
        }

        DisjointSet sets = new DisjointSet(keys.size());
        long merged = 0;
        if (maxEditDistance >= 0) {
            List<int[]> pairs = keysByTitle.entrySet().parallelStream()
                    .flatMap(e -> candidatePairs(e.getKey(), e.getValue(), trie, keyItems, maxEditDistance).stream())
                    .toList();
            for (int[] p : pairs) {
                if (sets.union(p[0], p[1])) {
                    merged++;
                }
            }
        }
        double nearMillis = (System.nanoTime() - t1) / 1e6;

        // 3. Build groups
        Map<Integer, List<Integer>> components = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            components.computeIfAbsent(sets.find(i), r -> new ArrayList<>()).add(i);
        }
        List<DuplicateGroup> groups = new ArrayList<>();
        for (List<Integer> comp : components.values()) {
            List<MediaItem> members = new ArrayList<>();
            for (int k : comp) {
                members.addAll(keyItems.get(k));
            }
            if (members.size() < 2) {
                continue;
            }
            members.sort(Comparator.comparingLong(MediaItem::sizeBytes).reversed().thenComparingInt(MediaItem::id));
            long total = 0;
            for (MediaItem m : members) {
                total += m.sizeBytes();
            }
            long reclaimable = total - members.get(0).sizeBytes();
            groups.add(new DuplicateGroup(0, comp.size() == 1 ? Kind.EXACT : Kind.NEAR,
                    List.copyOf(members), comp.size(), total, reclaimable));
        }
        groups.sort(Comparator.comparingLong(DuplicateGroup::reclaimableBytes).reversed()
                .thenComparing(g -> g.keep().normalizedTitle()));
        List<DuplicateGroup> numbered = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            DuplicateGroup g = groups.get(i);
            numbered.add(new DuplicateGroup(i + 1, g.kind(), g.items(), g.keyCount(), g.totalBytes(), g.reclaimableBytes()));
        }

        ScreeningStats stats = new ScreeningStats(bloom.bitSize(), bloom.hashCount(), bloom.memoryBytes(),
                bloom.targetFpp(), bloom.theoreticalFpp(), bloom.seed1(), bloom.seed2(), screener.offered(),
                screener.screenedOut(), screener.possiblyPresent(), screener.confirmedDuplicates(),
                screener.falsePositives(), screener.observedFalsePositiveRate(), keys.size(),
                keysByTitle.size(), merged, bloomMillis, nearMillis);
        return new Result(items, numbered, stats);
    }

    /**
     * Allowed edit distance for a pair of titles: the requested maximum, capped by length so short
     * titles are not merged by accident ("up" vs "us"): 0 below 5 chars, 1 below 9, then 2, ...
     */
    public static int effectiveDistance(int shorterLength, int maxEditDistance) {
        return Math.min(maxEditDistance, Math.max(0, (shorterLength - 1) / 4));
    }

    private static List<int[]> candidatePairs(String title, List<Integer> ownKeys, Trie<Integer> trie,
                                              List<List<MediaItem>> keyItems, int maxEditDistance) {
        List<int[]> out = new ArrayList<>();
        int searchDistance = effectiveDistance(title.length(), maxEditDistance);
        for (Trie.Match<Integer> m : trie.fuzzySearch(title, searchDistance)) {
            if (m.distance() > 0) {
                if (m.word().compareTo(title) < 0) {
                    continue; // the pair is produced from the other side
                }
                int allowed = effectiveDistance(Math.min(title.length(), m.word().length()), maxEditDistance);
                if (m.distance() > allowed || !digits(title).equals(digits(m.word()))) {
                    continue;
                }
            }
            for (int a : ownKeys) {
                for (int b : m.refs()) {
                    // same-title pairs come up twice (a,b) and (b,a); keep one. Cross-title pairs come up once.
                    boolean firstSighting = m.distance() > 0 || a < b;
                    if (a != b && firstSighting && compatible(keyItems.get(a).get(0), keyItems.get(b).get(0))) {
                        out.add(new int[]{a, b});
                    }
                }
            }
        }
        return out;
    }

    /** Same type and episode, and the same year when both years are known. */
    static boolean compatible(MediaItem a, MediaItem b) {
        return a.type().equals(b.type())
                && Objects.equals(a.season(), b.season())
                && Objects.equals(a.episode(), b.episode())
                && (a.year() == null || b.year() == null || a.year().equals(b.year()));
    }

    /** Sequel guard: "toy story 2" and "toy story 3" are one edit apart but are different films. */
    static String digits(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
