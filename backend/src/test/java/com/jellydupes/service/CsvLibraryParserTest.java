package com.jellydupes.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvLibraryParserTest {

    private static final String HEADER = "type,title,year,season,episode,path,size,runtime\n";
    private final CsvLibraryParser parser = new CsvLibraryParser();

    @Test
    void parsesQuotedFieldsAndNormalizes() {
        List<MediaItem> items = parser.parse(HEADER
                + "movie,\"Crouching Tiger, Hidden Dragon\",2000,,,\"/m/a, b.mkv\",100,120.4\n"
                + "episode,Breaking.Bad.S01E01.720p,2008,1,1,/tv/bb.mkv,5,58\r\n");
        assertEquals(2, items.size());
        MediaItem m = items.get(0);
        assertEquals("Crouching Tiger, Hidden Dragon", m.title());
        assertEquals("crouching tiger hidden dragon", m.normalizedTitle());
        assertEquals("/m/a, b.mkv", m.path());
        assertEquals(120, m.runtimeMinutes());
        assertEquals("movie|crouching tiger hidden dragon|2000", m.key());
        assertEquals("episode|breaking bad|2008|s1e1", items.get(1).key());
        assertNull(m.truthGroup());
    }

    @Test
    void columnsInAnyOrderAndBlankYearAllowed() {
        List<MediaItem> items = parser.parse("Title,Type,Size,Year,Season,Episode,Path,Runtime\nDune,movie,10,,,,/d.mkv,\n");
        assertEquals("dune", items.get(0).normalizedTitle());
        assertNull(items.get(0).year());
    }

    @Test
    void emptyFileRejected() {
        ValidationException e = assertThrows(ValidationException.class, () -> parser.parse(""));
        assertTrue(e.getMessage().contains("empty"));
        assertThrows(ValidationException.class, () -> parser.parse("\n\n"));
        assertThrows(ValidationException.class, () -> parser.parse(HEADER));
    }

    @Test
    void missingColumnsListed() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> parser.parse("type,title,year\nmovie,Dune,2021\n"));
        assertEquals("Missing required column(s): season, episode, path, size, runtime", e.getMessage());
    }

    @Test
    void badYearsAndBlankTitlesReportedWithLineNumbers() {
        ValidationException e = assertThrows(ValidationException.class, () -> parser.parse(HEADER
                + "movie,Dune,20x1,,,/a,1,1\n"
                + "movie,   ,2021,,,/b,1,1\n"
                + "movie,Old,1700,,,/c,1,1\n"
                + "movie,Big,2000,,,/d,lots,1\n"));
        assertEquals(List.of(
                "Line 2: year '20x1' is not a whole number.",
                "Line 3: title is blank.",
                "Line 4: year 1700 is out of range 1870-" + (java.time.Year.now().getValue() + 2) + ".",
                "Line 5: size 'lots' must be a byte count."), e.details());
    }
}
