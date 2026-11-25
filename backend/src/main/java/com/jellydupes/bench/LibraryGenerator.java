package com.jellydupes.bench;

import com.jellydupes.algo.Levenshtein;
import com.jellydupes.algo.Normalizer;
import com.jellydupes.service.DuplicateDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Seeded synthetic Jellyfin library with planted, labeled duplicates.
 *
 * <ul>
 *   <li>Every base title gets a label {@code dup_group}; its first record has {@code dup_kind=original}.</li>
 *   <li>Exact variants ({@code dup_kind=exact}): the same title written differently (scene-style names
 *       with quality tags and release groups, bracketed tags, case, punctuation). They normalize to
 *       the same key, so the Bloom/HashMap stage should find them.</li>
 *   <li>Near variants ({@code dup_kind=near}): one or two letter typos (substitute, delete, insert,
 *       adjacent swap) in titles long enough for the detector's length-scaled edit distance
 *       (normalized length &gt;= 9), measured to be 1-2 edits away. Digits are never changed.</li>
 * </ul>
 * The same seed always yields byte-identical output.
 */
public final class LibraryGenerator {

    public static final String HEADER = "type,title,year,season,episode,path,size,runtime,dup_group,dup_kind";

    private static final String[] WORDS = {
            "shadow", "river", "midnight", "garden", "empire", "silent", "golden", "winter", "summer", "falcon",
            "harbor", "crimson", "echo", "forest", "kingdom", "lantern", "mirror", "ocean", "phantom", "quiet",
            "rebel", "signal", "thunder", "valley", "whisper", "anchor", "breaker", "canyon", "dragon", "ember",
            "frontier", "glacier", "horizon", "island", "jungle", "knight", "legacy", "meadow", "nebula", "orbit",
            "prairie", "quest", "radiant", "saga", "tempest", "utopia", "voyage", "wanderer", "zenith", "atlas",
            "beacon", "cipher", "desert", "eclipse", "fortune", "gravity", "hollow", "infinite", "journey", "karma",
            "labyrinth", "monarch", "northern", "outpost", "paradox", "quarry", "requiem", "sentinel", "tidal",
            "undertow", "venom", "warden", "yonder", "alchemy", "blizzard", "carnival", "dynasty", "eternal",
            "fable", "guardian", "heritage", "inferno", "jasmine", "keystone", "lullaby", "marathon", "nomad",
            "oracle", "pilgrim", "quantum", "rhapsody", "sapphire", "twilight", "uprising", "vertigo", "wildfire",
            "apollo", "bandit", "cobalt", "delta", "express", "fugitive", "gambit", "hunter", "ivory", "jackal",
            "kaleidoscope", "lighthouse", "mercury", "neon", "obsidian", "pursuit", "ranger", "scarlet", "titan",
            "velvet", "wolf", "archive", "blueprint", "citadel", "domino", "enigma", "firefly", "gemini", "hazard",
            "iron", "jubilee", "kinetic", "lotus", "mosaic", "nightfall", "odyssey", "pendulum", "raven", "solstice",
            "tundra", "vagabond", "whirlwind", "almanac", "brigade", "chronicle", "dominion", "everest", "flux",
            "grotto", "harvest", "isotope", "juniper", "kestrel", "lagoon", "mariner", "nocturne", "overture",
            "polaris", "reckoning", "serpent", "trinity", "vanguard", "wreckage", "aurora", "bastion", "comet",
            "drifter", "elegy", "fjord", "granite", "hydra", "icarus", "jetstream", "kodiak", "lynx", "maverick",
            "noble", "outlaw", "prism", "relic", "spectre", "torrent", "vortex", "warrior", "avalanche", "brimstone",
            "cascade", "daybreak", "embassy", "foxglove", "gallows", "highland", "insomnia", "jigsaw", "kingpin",
            "longitude", "mustang", "nightshade", "orchid", "palisade", "riptide", "skyline", "talisman", "voltage"};
    private static final String[] RELEASE_GROUPS = {"SPARKS", "GECKOS", "NTb", "FLUX", "CMRG", "TEPES", "EVO", "AMIABLE"};
    private static final String[] BRACKET_TAGS = {"YTS.MX", "RARBG", "YIFY", "EZTV", "TGx"};
    private static final String[] RESOLUTIONS = {"2160p", "1080p", "720p", "480p"};
    private static final String[] SOURCES = {"BluRay", "WEB-DL", "WEBRip", "HDTV", "BDRip", "DVDRip"};
    private static final String[] CODECS = {"x264", "x265", "HEVC", "H.264", "AV1"};
    private static final double SERIES_SHARE = 0.2;
    private static final double EXACT_RATE = 0.10;
    private static final double SECOND_EXACT_RATE = 0.03;
    private static final double NEAR_RATE = 0.07;
    private static final int NEAR_MIN_LENGTH = 9;

    public record Row(String type, String title, int year, Integer season, Integer episode, String path,
                      long size, int runtime, String group, String kind) {
    }

    public record Dataset(long seed, List<Row> rows, int bases, int exactVariants, int nearVariants) {
        public String toCsv() {
            StringBuilder sb = new StringBuilder(rows.size() * 110);
            sb.append(HEADER).append('\n');
            for (Row r : rows) {
                sb.append(r.type()).append(',')
                        .append(csv(r.title())).append(',')
                        .append(r.year()).append(',')
                        .append(r.season() == null ? "" : r.season()).append(',')
                        .append(r.episode() == null ? "" : r.episode()).append(',')
                        .append(csv(r.path())).append(',')
                        .append(r.size()).append(',')
                        .append(r.runtime()).append(',')
                        .append(r.group()).append(',')
                        .append(r.kind()).append('\n');
            }
            return sb.toString();
        }
    }

    public Dataset generate(int records, long seed) {
        if (records < 1) {
            throw new IllegalArgumentException("records must be >= 1");
        }
        Random rnd = new Random(seed);
        List<Row> rows = new ArrayList<>(records + 4);
        Set<String> usedTitles = new HashSet<>();
        int bases = 0;
        int exact = 0;
        int near = 0;
        while (rows.size() < records) {
            boolean series = rnd.nextDouble() < SERIES_SHARE;
            String title = uniqueTitle(rnd, usedTitles);
            int year = 1950 + rnd.nextInt(76);
            int episodes = series ? 3 + rnd.nextInt(10) : 1;
            int season = 1 + rnd.nextInt(5);
            for (int e = 1; e <= episodes && rows.size() < records; e++) {
                String group = "g" + bases++;
                Integer s = series ? season : null;
                Integer ep = series ? e : null;
                String type = series ? "episode" : "movie";
                rows.add(row(rnd, type, title, year, s, ep, group, "original"));
                if (rows.size() < records && rnd.nextDouble() < EXACT_RATE) {
                    rows.add(row(rnd, type, exactVariant(rnd, title, year), year, s, ep, group, "exact"));
                    exact++;
                    if (rows.size() < records && rnd.nextDouble() < SECOND_EXACT_RATE / EXACT_RATE) {
                        rows.add(row(rnd, type, exactVariant(rnd, title, year), year, s, ep, group, "exact"));
                        exact++;
                    }
                }
                if (rows.size() < records && rnd.nextDouble() < NEAR_RATE
                        && Normalizer.normalizeTitle(title).length() >= NEAR_MIN_LENGTH) {
                    String typo = nearVariant(rnd, title);
                    if (rnd.nextBoolean()) {
                        typo = exactVariant(rnd, typo, year);
                    }
                    rows.add(row(rnd, type, typo, year, s, ep, group, "near"));
                    near++;
                }
            }
        }
        Collections.shuffle(rows, rnd);
        return new Dataset(seed, List.copyOf(rows), bases, exact, near);
    }

    private static String uniqueTitle(Random rnd, Set<String> used) {
        while (true) {
            int words = 1 + rnd.nextInt(4);
            StringBuilder sb = new StringBuilder();
            if (rnd.nextDouble() < 0.2) {
                sb.append("The ");
            }
            for (int i = 0; i < words; i++) {
                if (i > 0) {
                    sb.append(i == words - 1 && words > 2 && rnd.nextDouble() < 0.15 ? " of the " : " ");
                }
                sb.append(capitalize(WORDS[rnd.nextInt(WORDS.length)]));
            }
            if (rnd.nextDouble() < 0.05) {
                sb.append(' ').append(2 + rnd.nextInt(3));
            }
            String title = sb.toString();
            if (used.add(Normalizer.normalizeTitle(title))) {
                return title;
            }
        }
    }

    /** Same normalized key, different spelling on disk. */
    static String exactVariant(Random rnd, String title, int year) {
        return switch (rnd.nextInt(5)) {
            case 0 -> title.replace(' ', '.') + "." + year + "." + pick(rnd, RESOLUTIONS) + "." + pick(rnd, SOURCES)
                    + "." + pick(rnd, CODECS) + "-" + pick(rnd, RELEASE_GROUPS);
            case 1 -> title + " (" + year + ") [" + pick(rnd, BRACKET_TAGS) + "]";
            case 2 -> title.toUpperCase(Locale.ROOT);
            case 3 -> title.replaceFirst(" ", ": ") + "!";
            default -> title + " - " + pick(rnd, RESOLUTIONS) + " " + pick(rnd, SOURCES);
        };
    }

    /** One or two letter edits, verified to be 1-2 Levenshtein edits after normalization. */
    static String nearVariant(Random rnd, String title) {
        String normalized = Normalizer.normalizeTitle(title);
        int allowed = DuplicateDetector.effectiveDistance(normalized.length(), 2);
        for (int attempt = 0; attempt < 50; attempt++) {
            String t = title;
            int edits = allowed >= 2 && rnd.nextBoolean() ? 2 : 1;
            for (int i = 0; i < edits; i++) {
                t = typo(rnd, t);
            }
            String n = Normalizer.normalizeTitle(t);
            int d = Levenshtein.distance(normalized, n);
            if (d >= 1 && d <= allowed && n.length() >= NEAR_MIN_LENGTH) {
                return t;
            }
        }
        return typo(rnd, title);
    }

    private static String typo(Random rnd, String t) {
        List<Integer> letters = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetter(t.charAt(i))) {
                letters.add(i);
            }
        }
        int pos = letters.get(rnd.nextInt(letters.size()));
        char replacement = (char) ('a' + rnd.nextInt(26));
        return switch (rnd.nextInt(4)) {
            case 0 -> { // substitute
                char c = Character.toLowerCase(t.charAt(pos)) == replacement ? (char) ('a' + (replacement - 'a' + 1) % 26) : replacement;
                yield t.substring(0, pos) + c + t.substring(pos + 1);
            }
            case 1 -> letters.size() > 1 ? t.substring(0, pos) + t.substring(pos + 1) : t; // delete
            case 2 -> t.substring(0, pos) + replacement + t.substring(pos); // insert
            default -> { // swap with next letter
                if (pos + 1 < t.length() && Character.isLetter(t.charAt(pos + 1))) {
                    yield t.substring(0, pos) + t.charAt(pos + 1) + t.charAt(pos) + t.substring(pos + 2);
                }
                yield t.substring(0, pos) + replacement + t.substring(pos);
            }
        };
    }

    private static Row row(Random rnd, String type, String title, int year, Integer season, Integer episode,
                           String group, String kind) {
        boolean ep = season != null;
        long size = ep ? 200_000_000L + (long) (rnd.nextDouble() * 2_800_000_000L)
                : 700_000_000L + (long) (rnd.nextDouble() * 29_300_000_000L);
        int runtime = ep ? 20 + rnd.nextInt(41) : 80 + rnd.nextInt(101);
        String folder = Normalizer.normalizeTitle(title).replace(' ', '_');
        String path = ep
                ? String.format("/media/tv/%s/Season %02d/%s - S%02dE%02d - %s.mkv", folder, season, folder, season, episode, kind)
                : String.format("/media/movies/%s (%d)/%s-%s-%d.mkv", folder, year, folder, kind, rnd.nextInt(1000));
        return new Row(type, title, year, season, episode, path, size, runtime, group, kind);
    }

    private static String pick(Random rnd, String[] values) {
        return values[rnd.nextInt(values.length)];
    }

    private static String capitalize(String w) {
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    private static String csv(String v) {
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
