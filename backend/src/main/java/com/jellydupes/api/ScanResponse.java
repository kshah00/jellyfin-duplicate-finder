package com.jellydupes.api;

import com.jellydupes.service.AccuracyEvaluator;
import com.jellydupes.service.DuplicateDetector;
import com.jellydupes.service.MediaItem;
import com.jellydupes.service.ScanService;

import java.util.ArrayList;
import java.util.List;

/** JSON body of POST /api/scan. */
public record ScanResponse(Summary summary, DuplicateDetector.ScreeningStats screening,
                           AccuracyEvaluator.Accuracy accuracy, List<Group> groups, boolean groupsTruncated) {

    public static final int MAX_GROUPS = 2_000;

    public record Summary(String fileName, int records, int uniqueKeys, int duplicateGroups, int exactGroups,
                          int nearGroups, int duplicateItems, long totalBytes, long reclaimableBytes,
                          double fpRate, int maxDistance, double parseMillis, double scanMillis) {
    }

    public record Group(int id, String kind, String title, String normalizedTitle, Integer year, String type,
                        Integer season, Integer episode, int itemCount, int keyCount, long totalBytes,
                        long reclaimableBytes, List<Item> items) {
    }

    public record Item(int line, String title, Integer year, String path, long sizeBytes, Integer runtimeMinutes,
                       String key, boolean keep) {
    }

    static ScanResponse from(ScanService.ScanOutcome outcome, String fileName, double fpRate, int maxDistance) {
        DuplicateDetector.Result r = outcome.result();
        DuplicateDetector.ScreeningStats s = r.stats();
        int exact = 0;
        int near = 0;
        int dupItems = 0;
        List<Group> groups = new ArrayList<>();
        for (DuplicateDetector.DuplicateGroup g : r.groups()) {
            if (g.kind() == DuplicateDetector.Kind.EXACT) {
                exact++;
            } else {
                near++;
            }
            dupItems += g.items().size();
            if (groups.size() < MAX_GROUPS) {
                MediaItem keep = g.keep();
                List<Item> items = new ArrayList<>();
                for (MediaItem m : g.items()) {
                    items.add(new Item(m.line(), m.title(), m.year(), m.path(), m.sizeBytes(), m.runtimeMinutes(),
                            m.key(), m == keep));
                }
                groups.add(new Group(g.id(), g.kind().name(), keep.title(), keep.normalizedTitle(), keep.year(),
                        keep.type(), keep.season(), keep.episode(), g.items().size(), g.keyCount(), g.totalBytes(),
                        g.reclaimableBytes(), items));
            }
        }
        Summary summary = new Summary(fileName, r.items().size(), s.uniqueKeys(), r.groups().size(), exact, near,
                dupItems, r.totalBytes(), r.reclaimableBytes(), fpRate, maxDistance, outcome.parseMillis(),
                s.bloomMillis() + s.nearMillis());
        return new ScanResponse(summary, s, outcome.accuracy(), groups, r.groups().size() > MAX_GROUPS);
    }
}
