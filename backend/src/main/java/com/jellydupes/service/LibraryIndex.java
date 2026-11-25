package com.jellydupes.service;

import com.jellydupes.algo.Trie;

import java.util.List;

/**
 * Searchable snapshot of the most recently scanned library: a trie of titles (normalized on
 * insert) whose references are the library records. Immutable once built, so reads are thread-safe.
 */
public final class LibraryIndex {

    private final Trie<MediaItem> trie = new Trie<>();
    private final String source;
    private final int records;

    public LibraryIndex(String source, List<MediaItem> items) {
        this.source = source;
        this.records = items.size();
        for (MediaItem item : items) {
            trie.insert(item.title(), item);
        }
    }

    public Trie<MediaItem> trie() {
        return trie;
    }

    public String source() {
        return source;
    }

    public int records() {
        return records;
    }
}
