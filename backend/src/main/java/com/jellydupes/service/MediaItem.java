package com.jellydupes.service;

/**
 * One row of a Jellyfin library export, plus its normalized title and exact-duplicate key.
 *
 * @param truthGroup optional ground-truth duplicate label (synthetic datasets only, column dup_group)
 * @param truthKind  optional ground-truth kind: original, exact or near (column dup_kind)
 */
public record MediaItem(
        int id,
        int line,
        String type,
        String title,
        Integer year,
        Integer season,
        Integer episode,
        String path,
        long sizeBytes,
        Integer runtimeMinutes,
        String normalizedTitle,
        String key,
        String truthGroup,
        String truthKind) {

    public boolean isEpisodic() {
        return season != null || episode != null;
    }
}
