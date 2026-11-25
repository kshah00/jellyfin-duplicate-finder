package com.jellydupes.algo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Baseline for fuzzy search: compute the full Levenshtein distance from the query to every title.
 * O(N * |q| * |t|) per query; used to validate and benchmark {@link Trie#fuzzySearch}.
 */
public final class BruteForceMatcher {

    public record Match(String word, int distance) {
    }

    private final List<String> words;

    public BruteForceMatcher(Collection<String> distinctWords) {
        this.words = new ArrayList<>(distinctWords);
    }

    public List<Match> search(String query, int maxDistance) {
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance must be >= 0");
        }
        List<Match> out = new ArrayList<>();
        for (String w : words) {
            int d = Levenshtein.distance(query, w);
            if (d <= maxDistance) {
                out.add(new Match(w, d));
            }
        }
        out.sort(Comparator.comparingInt(Match::distance).thenComparing(Match::word));
        return out;
    }

    public int size() {
        return words.size();
    }
}
