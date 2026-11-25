package com.jellydupes.service;

import com.jellydupes.algo.Normalizer;
import com.jellydupes.algo.Trie;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Autocomplete: trie prefix matches first, then fuzzy (typo-tolerant) matches to fill the list. */
@Service
public class SearchService {

    public enum MatchType { PREFIX, FUZZY }

    public record Suggestion(String title, String displayTitle, Integer year, String type,
                             MatchType matchType, int distance, int copies) {
    }

    public record SearchResult(String query, String normalizedQuery, String source, int indexedTitles,
                               List<Suggestion> suggestions) {
    }

    private final ScanService scanService;

    public SearchService(ScanService scanService) {
        this.scanService = scanService;
    }

    public SearchResult search(String query, int limit, int maxDistance) {
        if (query == null || query.isBlank()) {
            throw new ValidationException("Query parameter 'q' must not be blank.");
        }
        if (limit < 1 || limit > 100) {
            throw new ValidationException("limit must be between 1 and 100, got " + limit);
        }
        if (maxDistance < 0 || maxDistance > 3) {
            throw new ValidationException("maxDistance must be between 0 and 3, got " + maxDistance);
        }
        LibraryIndex index = scanService.currentIndex();
        Trie<MediaItem> trie = index.trie();
        String normalized = Normalizer.normalizeTitle(query);
        List<Suggestion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Trie.Entry<MediaItem> e : trie.prefixSearch(normalized, limit)) {
            seen.add(e.word());
            out.add(toSuggestion(e.word(), e.refs(), MatchType.PREFIX, 0));
        }
        if (out.size() < limit && normalized.length() >= 3 && maxDistance > 0) {
            for (Trie.Match<MediaItem> m : trie.fuzzySearch(normalized, maxDistance)) {
                if (out.size() >= limit) {
                    break;
                }
                if (seen.add(m.word())) {
                    out.add(toSuggestion(m.word(), m.refs(), MatchType.FUZZY, m.distance()));
                }
            }
        }
        return new SearchResult(query, normalized, index.source(), trie.size(), out);
    }

    private static Suggestion toSuggestion(String word, List<MediaItem> refs, MatchType type, int distance) {
        MediaItem first = refs.get(0);
        return new Suggestion(word, first.title(), first.year(), first.type(), type, distance, refs.size());
    }
}
