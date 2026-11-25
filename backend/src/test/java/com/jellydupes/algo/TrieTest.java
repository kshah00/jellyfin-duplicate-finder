package com.jellydupes.algo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrieTest {

    private static List<String> words(List<? extends Trie.Entry<?>> entries) {
        return entries.stream().map(Trie.Entry::word).toList();
    }

    private static List<String> fuzzyWords(List<? extends Trie.Match<?>> matches) {
        return matches.stream().map(m -> m.word() + ":" + m.distance()).toList();
    }

    @Test
    @DisplayName("insert then lookup finds the item; missing words and partial prefixes are absent")
    void insertAndLookup() {
        Trie<Integer> trie = Trie.identity();
        trie.insert("inception", 1);
        trie.insert("interstellar", 2);
        trie.insert("in", 3);
        assertEquals(List.of(1), trie.get("inception"));
        assertEquals(List.of(2), trie.get("interstellar"));
        assertEquals(List.of(3), trie.get("in"));
        assertTrue(trie.contains("in"));
        assertFalse(trie.contains("inc"), "a prefix is not a stored word");
        assertFalse(trie.contains("inceptions"));
        assertEquals(List.of(), trie.get("dune"));
        assertEquals(3, trie.size());
    }

    @Test
    @DisplayName("prefix search returns all and only the matching words, alphabetically")
    void prefixSearchAllAndOnly() {
        Random rnd = new Random(5);
        Set<String> all = new LinkedHashSet<>();
        Trie<String> trie = Trie.identity();
        while (all.size() < 2_000) {
            String w = randomWord(rnd, 1 + rnd.nextInt(8));
            if (all.add(w)) {
                trie.insert(w, w);
            }
        }
        for (String prefix : List.of("a", "ab", "q", "zz", "mno", "abcdefgh", "xyzxyzxyzq")) {
            List<String> expected = all.stream().filter(w -> w.startsWith(prefix)).sorted().toList();
            assertEquals(expected, words(trie.prefixSearch(prefix, 0)), "prefix " + prefix);
        }
        assertEquals(5, trie.prefixSearch("a", 5).size(), "limit is respected");
    }

    @Test
    @DisplayName("boundary: empty string and single-character words")
    void emptyAndSingleCharacter() {
        Trie<Integer> trie = Trie.identity();
        assertThrows(IllegalArgumentException.class, () -> trie.insert("", 1));
        assertThrows(IllegalArgumentException.class, () -> trie.insert(null, 1));
        assertEquals(List.of(), trie.prefixSearch("", 0), "empty trie has no words");
        assertEquals(List.of(), trie.fuzzySearch("a", 2));

        trie.insert("a", 1);
        trie.insert("b", 2);
        trie.insert("ab", 3);
        assertEquals(List.of(1), trie.get("a"));
        assertEquals(List.of("a", "ab", "b"), words(trie.prefixSearch("", 0)), "empty prefix returns everything");
        assertEquals(List.of("a", "ab"), words(trie.prefixSearch("a", 0)));
        assertEquals(List.of("a:0", "ab:1", "b:1"), fuzzyWords(trie.fuzzySearch("a", 1)));
        assertEquals(List.of(), trie.get(""));
        // empty query: every word within distance = its length
        assertEquals(List.of("a:1", "b:1"), fuzzyWords(trie.fuzzySearch("", 1)));
    }

    @Test
    @DisplayName("inserting the same title twice keeps both references")
    void repeatedInsertKeepsBothReferences() {
        Trie<String> trie = Trie.identity();
        trie.insert("the matrix", "/media/a.mkv");
        trie.insert("the matrix", "/media/b.mkv");
        assertEquals(List.of("/media/a.mkv", "/media/b.mkv"), trie.get("the matrix"));
        assertEquals(1, trie.size(), "still one distinct word");
        assertEquals(2, trie.refCount());
        assertEquals(2, trie.prefixSearch("the m", 0).get(0).refs().size());
    }

    @Test
    @DisplayName("fuzzy search at distance 0, 1 and 2")
    void fuzzyDistances() {
        Trie<Integer> trie = Trie.identity();
        String[] titles = {"inception", "interception", "inceptoin", "incepton", "deception", "dune"};
        for (int i = 0; i < titles.length; i++) {
            trie.insert(titles[i], i);
        }
        assertEquals(List.of("inception:0"), fuzzyWords(trie.fuzzySearch("inception", 0)));
        assertEquals(List.of(), fuzzyWords(trie.fuzzySearch("inceptio", 0)));
        assertEquals(List.of("inception:0", "incepton:1"), fuzzyWords(trie.fuzzySearch("inception", 1)));
        assertEquals(List.of("inception:0", "incepton:1", "deception:2", "inceptoin:2"),
                fuzzyWords(trie.fuzzySearch("inception", 2)));
        assertThrows(IllegalArgumentException.class, () -> trie.fuzzySearch("x", -1));
    }

    @Test
    @DisplayName("fuzzy search equals brute-force Levenshtein on 1,000 random titles")
    void fuzzyEqualsBruteForce() {
        Random rnd = new Random(2024);
        Set<String> titles = new LinkedHashSet<>();
        String[] vocab = {"star", "wars", "the", "dark", "knight", "rises", "toy", "story", "dune", "matrix",
                "alien", "aliens", "heat", "up", "us", "her", "it", "night", "light", "fight", "club"};
        while (titles.size() < 1_000) {
            int n = 1 + rnd.nextInt(3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                sb.append(i == 0 ? "" : " ").append(rnd.nextBoolean() ? vocab[rnd.nextInt(vocab.length)] : randomWord(rnd, 2 + rnd.nextInt(5)));
            }
            titles.add(sb.toString());
        }
        Trie<String> trie = Trie.identity();
        titles.forEach(t -> trie.insert(t, t));
        BruteForceMatcher brute = new BruteForceMatcher(titles);
        List<String> list = new ArrayList<>(titles);
        for (int q = 0; q < 150; q++) {
            String query = list.get(rnd.nextInt(list.size()));
            if (q % 3 == 1) {
                query = query.substring(0, rnd.nextInt(query.length()));
            } else if (q % 3 == 2) {
                query = query + randomWord(rnd, 1);
            }
            for (int d = 0; d <= 2; d++) {
                List<String> expected = brute.search(query, d).stream().map(m -> m.word() + ":" + m.distance()).toList();
                assertEquals(expected, fuzzyWords(trie.fuzzySearch(query, d)), "query '" + query + "' d=" + d);
            }
        }
    }

    @Test
    @DisplayName("keys are normalized before insert and before lookup")
    void normalizationBeforeInsert() {
        Trie<String> trie = new Trie<>();
        String stored = trie.insert("The.Matrix.1999.1080p.BluRay.x264-SPARKS", "scene");
        assertEquals("the matrix", stored);
        trie.insert("THE MATRIX", "caps");
        trie.insert("The Matrix [YTS.MX]", "tagged");
        assertEquals(1, trie.size());
        assertEquals(List.of("scene", "caps", "tagged"), trie.get("the matrix"));
        assertEquals(List.of("scene", "caps", "tagged"), trie.get("The Matrix!"));
        assertEquals(List.of("the matrix"), words(trie.prefixSearch("THE MAT", 0)));
        assertThrows(IllegalArgumentException.class, () -> trie.insert("1080p BluRay x264", "only-tags"));
    }

    private static String randomWord(Random rnd, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + rnd.nextInt(26)));
        }
        return sb.toString();
    }
}
