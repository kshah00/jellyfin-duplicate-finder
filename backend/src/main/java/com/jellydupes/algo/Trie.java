package com.jellydupes.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Character trie over normalized titles. Each terminal node stores the normalized word and every
 * item reference inserted under it (repeated inserts keep all references).
 *
 * <ul>
 *   <li>{@link #prefixSearch}: walk to the prefix node, then DFS its subtree (autocomplete).</li>
 *   <li>{@link #fuzzySearch}: DFS the whole trie carrying one Levenshtein DP row per node. A child's
 *       row is computed from its parent's row in O(|q|). If the minimum of a row exceeds the max
 *       edit distance, no word below that node can match, so the branch is pruned.</li>
 * </ul>
 *
 * Keys are passed through a normalizer before insert and before every query.
 */
public final class Trie<T> {

    public record Entry<T>(String word, List<T> refs) {
    }

    public record Match<T>(String word, int distance, List<T> refs) {
    }

    private static final class Node<T> {
        final Map<Character, Node<T>> children = new HashMap<>(4);
        String word;
        List<T> refs;
    }

    private final Node<T> root = new Node<>();
    private final Function<String, String> normalizer;
    private int wordCount;
    private int nodeCount = 1;
    private long refCount;

    /** Trie that normalizes keys with {@link Normalizer#normalizeTitle(String)}. */
    public Trie() {
        this(Normalizer::normalizeTitle);
    }

    public Trie(Function<String, String> normalizer) {
        this.normalizer = normalizer;
    }

    /** Trie that stores keys exactly as given (caller has already normalized them). */
    public static <T> Trie<T> identity() {
        return new Trie<>(Function.identity());
    }

    /**
     * Inserts a reference under the normalized key and returns that normalized key.
     *
     * @throws IllegalArgumentException if the key is null or normalizes to the empty string
     */
    public String insert(String key, T ref) {
        String word = normalize(key);
        if (word.isEmpty()) {
            throw new IllegalArgumentException("cannot insert an empty key (raw: '" + key + "')");
        }
        Node<T> node = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            Node<T> next = node.children.get(c);
            if (next == null) {
                next = new Node<>();
                node.children.put(c, next);
                nodeCount++;
            }
            node = next;
        }
        if (node.word == null) {
            node.word = word;
            node.refs = new ArrayList<>(1);
            wordCount++;
        }
        node.refs.add(ref);
        refCount++;
        return word;
    }

    /** All references stored under exactly this (normalized) key, or an empty list. */
    public List<T> get(String key) {
        Node<T> node = find(normalize(key));
        return node == null || node.word == null ? List.of() : Collections.unmodifiableList(node.refs);
    }

    public boolean contains(String key) {
        Node<T> node = find(normalize(key));
        return node != null && node.word != null;
    }

    /**
     * Words starting with the normalized prefix, alphabetically. An empty prefix returns every word.
     *
     * @param limit maximum results; {@code <= 0} means unlimited
     */
    public List<Entry<T>> prefixSearch(String prefix, int limit) {
        List<Entry<T>> out = new ArrayList<>();
        Node<T> start = find(normalize(prefix));
        if (start == null) {
            return out;
        }
        int max = limit <= 0 ? Integer.MAX_VALUE : limit;
        Deque<Node<T>> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty() && out.size() < max) {
            Node<T> node = stack.pop();
            if (node.word != null) {
                out.add(new Entry<>(node.word, Collections.unmodifiableList(node.refs)));
            }
            // push in reverse order so the smallest character is popped first (alphabetical DFS)
            List<Character> keys = new ArrayList<>(node.children.keySet());
            keys.sort(Comparator.reverseOrder());
            for (Character c : keys) {
                stack.push(node.children.get(c));
            }
        }
        return out;
    }

    /**
     * Every word within {@code maxDistance} Levenshtein edits of the normalized query, sorted by
     * distance then word.
     */
    public List<Match<T>> fuzzySearch(String query, int maxDistance) {
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance must be >= 0, got " + maxDistance);
        }
        String q = normalize(query);
        List<Match<T>> out = new ArrayList<>();
        int[] firstRow = new int[q.length() + 1];
        for (int j = 0; j <= q.length(); j++) {
            firstRow[j] = j;
        }
        // The empty word is never stored, so only the children of the root need visiting.
        for (Map.Entry<Character, Node<T>> e : root.children.entrySet()) {
            fuzzyVisit(e.getValue(), e.getKey(), q, firstRow, maxDistance, out);
        }
        out.sort(Comparator.<Match<T>>comparingInt(Match::distance).thenComparing(Match::word));
        return out;
    }

    private void fuzzyVisit(Node<T> node, char c, String q, int[] prevRow, int maxDistance, List<Match<T>> out) {
        int cols = q.length() + 1;
        int[] row = new int[cols];
        row[0] = prevRow[0] + 1;
        int rowMin = row[0];
        for (int j = 1; j < cols; j++) {
            int insert = row[j - 1] + 1;
            int delete = prevRow[j] + 1;
            int replace = prevRow[j - 1] + (q.charAt(j - 1) == c ? 0 : 1);
            int v = Math.min(Math.min(insert, delete), replace);
            row[j] = v;
            if (v < rowMin) {
                rowMin = v;
            }
        }
        if (node.word != null && row[cols - 1] <= maxDistance) {
            out.add(new Match<>(node.word, row[cols - 1], Collections.unmodifiableList(node.refs)));
        }
        if (rowMin <= maxDistance) {
            for (Map.Entry<Character, Node<T>> e : node.children.entrySet()) {
                fuzzyVisit(e.getValue(), e.getKey(), q, row, maxDistance, out);
            }
        }
    }

    private Node<T> find(String word) {
        Node<T> node = root;
        for (int i = 0; i < word.length() && node != null; i++) {
            node = node.children.get(word.charAt(i));
        }
        return node;
    }

    private String normalize(String key) {
        if (key == null) {
            return "";
        }
        String n = normalizer.apply(key);
        return n == null ? "" : n;
    }

    /** Number of distinct words. */
    public int size() {
        return wordCount;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public long refCount() {
        return refCount;
    }
}
