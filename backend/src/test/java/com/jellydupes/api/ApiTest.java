package com.jellydupes.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    @Autowired
    MockMvc mvc;

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "lib.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void sampleCanBeDownloadedAndScanned() throws Exception {
        String sample = mvc.perform(get("/api/sample"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("type,title,year")))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(multipart("/api/scan").file(csv(sample)).param("fpRate", "0.01").param("maxDistance", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.records").value(41))
                .andExpect(jsonPath("$.summary.duplicateGroups", greaterThan(5)))
                .andExpect(jsonPath("$.summary.reclaimableBytes", greaterThan(0L)))
                .andExpect(jsonPath("$.screening.offered").value(41))
                .andExpect(jsonPath("$.groups[0].items[0].keep").value(true));
    }

    @Test
    void searchAutocompletesAndToleratesTypos() throws Exception {
        mvc.perform(get("/api/search").param("q", "The Ma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedQuery").value("the ma"))
                .andExpect(jsonPath("$.suggestions[*].title", hasItems("the matrix", "the matirx")))
                .andExpect(jsonPath("$.suggestions[0].matchType").value("PREFIX"));
        mvc.perform(get("/api/search").param("q", "interstelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title").value("interstellar"))
                .andExpect(jsonPath("$.suggestions[0].matchType").value("FUZZY"))
                .andExpect(jsonPath("$.suggestions[0].distance").value(1));
        mvc.perform(get("/api/search").param("q", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationErrorsAre400WithClearMessages() throws Exception {
        mvc.perform(multipart("/api/scan").file(csv("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("non-empty")));
        mvc.perform(multipart("/api/scan").file(csv("type,title\nmovie,Dune\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Missing required column(s): year")));
        mvc.perform(multipart("/api/scan").file(csv("type,title,year,season,episode,path,size,runtime\nmovie,,abc,,,/a,1,1\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("Line 2: title is blank."))
                .andExpect(jsonPath("$.details[1]").value("Line 2: year 'abc' is not a whole number."));
        mvc.perform(multipart("/api/scan").file(csv("type,title,year,season,episode,path,size,runtime\nmovie,Dune,2021,,,/a,1,1\n"))
                        .param("fpRate", "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("fpRate")));
        mvc.perform(post("/api/scan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("multipart/form-data")));
        mvc.perform(multipart("/api/scan").file(csv("x")).param("maxDistance", "two"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("maxDistance")));
    }

    @Test
    void smallBenchmarkRuns() throws Exception {
        mvc.perform(post("/api/benchmark").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizes\":[300],\"queries\":20,\"warmupRuns\":1,\"measuredRuns\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].records").value(300))
                .andExpect(jsonPath("$.results[0].dedupe.answersAgree").value(true))
                .andExpect(jsonPath("$.results[0].fuzzy.answersAgree").value(true))
                .andExpect(jsonPath("$.machine.javaVersion").exists());
        mvc.perform(post("/api/benchmark").contentType(MediaType.APPLICATION_JSON).content("{\"sizes\":[5]}"))
                .andExpect(status().isBadRequest());
    }
}
