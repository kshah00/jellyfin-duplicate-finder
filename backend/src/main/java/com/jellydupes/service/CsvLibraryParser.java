package com.jellydupes.service;

import com.jellydupes.algo.Normalizer;
import com.jellydupes.algo.Normalizer.NormalizedTitle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a Jellyfin library export CSV (RFC 4180 quoting) into {@link MediaItem}s and validates it.
 * Required columns: type, title, year, season, episode, path, size, runtime (any order, case-insensitive).
 * Optional columns: dup_group, dup_kind (ground-truth labels written by the synthetic generator).
 */
public final class CsvLibraryParser {

    public static final List<String> REQUIRED_COLUMNS =
            List.of("type", "title", "year", "season", "episode", "path", "size", "runtime");
    private static final int MAX_REPORTED_ERRORS = 25;
    private static final int MIN_YEAR = 1870;

    public List<MediaItem> parse(InputStream in) throws IOException {
        return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public List<MediaItem> parse(String csv) {
        try {
            return parse(new StringReader(csv));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<MediaItem> parse(Reader reader) throws IOException {
        List<List<String>> rows = readRows(new BufferedReader(reader));
        while (!rows.isEmpty() && isBlankRow(rows.get(rows.size() - 1))) {
            rows.remove(rows.size() - 1);
        }
        if (rows.isEmpty()) {
            throw new ValidationException("The uploaded file is empty.");
        }
        Map<String, Integer> col = headerIndex(rows.get(0));
        List<String> missing = new ArrayList<>();
        for (String c : REQUIRED_COLUMNS) {
            if (!col.containsKey(c)) {
                missing.add(c);
            }
        }
        if (!missing.isEmpty()) {
            throw new ValidationException("Missing required column(s): " + String.join(", ", missing),
                    List.of("Expected header: " + String.join(",", REQUIRED_COLUMNS)));
        }
        if (rows.size() == 1) {
            throw new ValidationException("The file has a header but no data rows.");
        }

        int maxYear = Year.now().getValue() + 2;
        List<String> errors = new ArrayList<>();
        List<MediaItem> items = new ArrayList<>(rows.size() - 1);
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int line = r + 1;
            if (isBlankRow(row)) {
                continue;
            }
            int errorsBefore = errors.size();
            String type = cell(row, col, "type").toLowerCase(Locale.ROOT);
            if (type.isEmpty()) {
                type = "movie";
            }
            String title = cell(row, col, "title");
            if (title.isEmpty()) {
                errors.add("Line " + line + ": title is blank.");
            }
            Integer year = parseInt(cell(row, col, "year"), "year", line, errors);
            if (year != null && (year < MIN_YEAR || year > maxYear)) {
                errors.add("Line " + line + ": year " + year + " is out of range " + MIN_YEAR + "-" + maxYear + ".");
            }
            Integer season = parseInt(cell(row, col, "season"), "season", line, errors);
            Integer episode = parseInt(cell(row, col, "episode"), "episode", line, errors);
            if (season != null && season < 0 || episode != null && episode < 0) {
                errors.add("Line " + line + ": season/episode must not be negative.");
            }
            long size = parseSize(cell(row, col, "size"), line, errors);
            Integer runtime = parseRuntime(cell(row, col, "runtime"), line, errors);

            if (errors.size() > errorsBefore) {
                if (errors.size() >= MAX_REPORTED_ERRORS) {
                    errors.add("Stopped after " + MAX_REPORTED_ERRORS + " errors.");
                    break;
                }
                continue;
            }
            NormalizedTitle nt = Normalizer.normalize(title, year);
            if (nt.title().isEmpty()) {
                errors.add("Line " + line + ": title '" + title + "' contains only punctuation or quality tags.");
                continue;
            }
            String key = Normalizer.key(type, nt.title(), nt.year(), season, episode);
            items.add(new MediaItem(items.size(), line, type, title, nt.year(), season, episode,
                    cell(row, col, "path"), size, runtime, nt.title(), key,
                    emptyToNull(cell(row, col, "dup_group")), emptyToNull(cell(row, col, "dup_kind"))));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("The CSV has " + errors.size() + " invalid value(s).", errors);
        }
        if (items.isEmpty()) {
            throw new ValidationException("The file has a header but no data rows.");
        }
        return items;
    }

    private static Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).trim().toLowerCase(Locale.ROOT);
            if (i == 0 && name.startsWith("﻿")) {
                name = name.substring(1);
            }
            idx.putIfAbsent(name, i);
        }
        return idx;
    }

    private static String cell(List<String> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null || i >= row.size() ? "" : row.get(i).trim();
    }

    private static Integer parseInt(String v, String field, int line, List<String> errors) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            errors.add("Line " + line + ": " + field + " '" + v + "' is not a whole number.");
            return null;
        }
    }

    private static long parseSize(String v, int line, List<String> errors) {
        if (v.isEmpty()) {
            return 0;
        }
        try {
            long size = Long.parseLong(v);
            if (size < 0) {
                errors.add("Line " + line + ": size must not be negative.");
            }
            return size;
        } catch (NumberFormatException e) {
            errors.add("Line " + line + ": size '" + v + "' must be a byte count.");
            return 0;
        }
    }

    private static Integer parseRuntime(String v, int line, List<String> errors) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            double minutes = Double.parseDouble(v);
            if (minutes < 0 || Double.isNaN(minutes) || Double.isInfinite(minutes)) {
                errors.add("Line " + line + ": runtime must be a non-negative number of minutes.");
                return null;
            }
            return (int) Math.round(minutes);
        } catch (NumberFormatException e) {
            errors.add("Line " + line + ": runtime '" + v + "' must be a number of minutes.");
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }

    private static boolean isBlankRow(List<String> row) {
        for (String c : row) {
            if (!c.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** Minimal RFC 4180 reader: quoted fields, escaped quotes, embedded commas and newlines. */
    static List<List<String>> readRows(BufferedReader r) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean any = false;
        int ch;
        while ((ch = r.read()) != -1) {
            any = true;
            char c = (char) ch;
            if (inQuotes) {
                if (c == '"') {
                    r.mark(1);
                    int next = r.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) {
                            r.reset();
                        }
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r') {
                    r.mark(1);
                    if (r.read() != '\n') {
                        r.reset();
                    }
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (any && (field.length() > 0 || !row.isEmpty())) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
