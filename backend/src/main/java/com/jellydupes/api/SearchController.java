package com.jellydupes.api;

import com.jellydupes.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** Autocomplete over the last scanned library (the bundled sample until something is uploaded). */
    @GetMapping("/search")
    public SearchService.SearchResult search(@RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "10") int limit,
                                             @RequestParam(defaultValue = "2") int maxDistance) {
        return searchService.search(q, limit, maxDistance);
    }
}
