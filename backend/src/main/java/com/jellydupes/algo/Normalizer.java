package com.jellydupes.algo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw library title into a comparison key.
 *
 * <ol>
 *   <li>Strip diacritics and lowercase.</li>
 *   <li>Pull a bracketed year like "(1999)" out of the title (kept separately).</li>
 *   <li>Drop bracketed release-group tags such as "[YTS.MX]" or "{RARBG}" and edition labels
 *       such as "Extended Edition".</li>
 *   <li>Remove apostrophes, turn other punctuation into spaces.</li>
 *   <li>Scene-style names ("Movie.Name.2010.1080p.BluRay.x264-GRP"): cut everything from the first
 *       quality tag onward, which also removes the trailing "-GRP" release group; a year right
 *       before the cut becomes the year.</li>
 *   <li>Remove any remaining quality tokens (and SxxEyy episode tags) and collapse whitespace.</li>
 * </ol>
 *
 * A trailing number is only treated as a year when it is the known year or the title is in scene
 * format, so "Blade Runner 2049" keeps its 2049.
 */
public final class Normalizer {

    public record NormalizedTitle(String title, Integer year) {
    }

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern BRACKET_YEAR = Pattern.compile("[(\\[{]\\s*((?:18|19|20)\\d{2})\\s*[)\\]}]");
    private static final Pattern BRACKETED = Pattern.compile("\\[[^\\]]*]|\\{[^}]*}");
    private static final Pattern EDITION = Pattern.compile(
            "\\b(extended|director'?s|theatrical|unrated|uncut|remastered|special|ultimate|collector'?s|anniversary)"
                    + "\\s+(cut|edition|version)\\b");
    private static final Pattern APOSTROPHES = Pattern.compile("['’`]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern RESOLUTION = Pattern.compile("\\d{3,4}[pi]");
    private static final Pattern AUDIO = Pattern.compile("(ddp?|eac3|ac3|aac|dts|truehd|flac)\\d*");
    private static final Pattern BIT_DEPTH = Pattern.compile("\\d{1,2}bit");
    private static final Pattern EPISODE_TAG = Pattern.compile("s\\d{1,2}e\\d{1,3}");
    private static final Pattern YEAR_TOKEN = Pattern.compile("(?:18|19|20)\\d{2}");

    /** Tags that never belong to a title; seeing one marks the start of a scene-style suffix. */
    private static final Set<String> QUALITY_TAGS = Set.of(
            "4k", "uhd", "hdr", "hdr10", "dolbyvision", "sdr",
            "bluray", "bdrip", "brrip", "bdremux", "remux", "webrip", "webdl", "hdtv", "pdtv",
            "dvdrip", "dvdscr", "dvd5", "dvd9", "hdrip", "hdcam", "camrip", "telesync",
            "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx", "av1", "vp9",
            "atmos", "dtshd", "yify", "yts", "rarbg", "ettv", "eztv");

    private Normalizer() {
    }

    /** Normalized title only. */
    public static String normalizeTitle(String raw) {
        return normalize(raw, null).title();
    }

    /**
     * @param raw       raw title from the library export
     * @param knownYear year from the export's year column, or null if blank
     * @return normalized title, plus the known year (or a year recovered from the title)
     */
    public static NormalizedTitle normalize(String raw, Integer knownYear) {
        if (raw == null) {
            return new NormalizedTitle("", knownYear);
        }
        Integer year = knownYear;
        String s = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);

        Matcher by = BRACKET_YEAR.matcher(s);
        if (by.find() && year == null) {
            year = Integer.parseInt(by.group(1));
        }
        s = BRACKET_YEAR.matcher(s).replaceAll(" ");
        s = BRACKETED.matcher(s).replaceAll(" ");
        s = EDITION.matcher(s).replaceAll(" ");
        s = s.replace("&", " and ");
        // join multi-part tags before punctuation splits them apart
        s = s.replaceAll("web[-. ]?dl", "webdl")
                .replaceAll("blu[-. ]?ray", "bluray")
                .replaceAll("dts[-. ]?hd", "dtshd")
                .replaceAll("\\b([hx])[.]?26([45])\\b", "$126$2")
                .replaceAll("hdr10\\+", "hdr10")
                .replaceAll("dolby[ .]?vision", "dolbyvision");
        s = APOSTROPHES.matcher(s).replaceAll("");
        s = NON_ALNUM.matcher(s).replaceAll(" ").trim();
        if (s.isEmpty()) {
            return new NormalizedTitle("", year);
        }

        String[] tokens = s.split(" ");
        int cut = tokens.length;
        for (int i = 0; i < tokens.length; i++) {
            if (isQualityTag(tokens[i])) {
                cut = i;
                break;
            }
        }
        boolean sceneFormat = cut > 0 && cut < tokens.length;
        int end = sceneFormat ? cut : tokens.length;
        List<String> kept = new ArrayList<>(end);
        for (int i = 0; i < end; i++) {
            if (!isQualityTag(tokens[i])) {
                kept.add(tokens[i]);
            }
        }

        if (kept.size() >= 2) {
            String last = kept.get(kept.size() - 1);
            if (YEAR_TOKEN.matcher(last).matches()) {
                int y = Integer.parseInt(last);
                if (year != null && y == year) {
                    kept.remove(kept.size() - 1);
                } else if (year == null && sceneFormat) {
                    year = y;
                    kept.remove(kept.size() - 1);
                }
            }
        }
        return new NormalizedTitle(String.join(" ", kept), year);
    }

    static boolean isQualityTag(String token) {
        return QUALITY_TAGS.contains(token)
                || RESOLUTION.matcher(token).matches()
                || AUDIO.matcher(token).matches()
                || BIT_DEPTH.matcher(token).matches()
                || EPISODE_TAG.matcher(token).matches();
    }

    /**
     * Exact-duplicate key: type, normalized title, year, and for episodes the season/episode.
     */
    public static String key(String type, String normalizedTitle, Integer year, Integer season, Integer episode) {
        StringBuilder sb = new StringBuilder(normalizedTitle.length() + 24);
        sb.append(type == null ? "" : type).append('|').append(normalizedTitle).append('|');
        if (year != null) {
            sb.append(year);
        }
        if (season != null || episode != null) {
            sb.append("|s").append(season == null ? "?" : season).append('e').append(episode == null ? "?" : episode);
        }
        return sb.toString();
    }
}
