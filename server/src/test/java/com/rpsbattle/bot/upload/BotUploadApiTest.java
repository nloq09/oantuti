package com.rpsbattle.bot.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "rps.bots.enabled=false")
@AutoConfigureMockMvc
class BotUploadApiTest {
    @TempDir
    static Path root;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("rps.bots.directory", root::toString);
    }

    @Test
    void multipartUploadReturnsCapabilityAndMissingRunnerDoesNotReserveASeat() throws Exception {
        var result = mvc.perform(multipart("/api/bots").file(new MockMultipartFile("file", "bot.py", "text/plain", "print(1)".getBytes())).param("name", "Python bot"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.language").value("PYTHON"))
                .andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.path").doesNotExist()).andReturn();
        var bot = json.readTree(result.getResponse().getContentAsString());
        String credentials = "\"botId\":\"" + bot.path("id").asText() + "\",\"botToken\":\"" + bot.path("token").asText() + "\"";
        mvc.perform(post("/api/matches").contentType("application/json").content("{\"mode\":\"HUMAN_VS_HUMAN\"," + credentials + "}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("BOT_SANDBOX_UNAVAILABLE"));
        var created = mvc.perform(post("/api/matches").contentType("application/json").content("{\"mode\":\"HUMAN_VS_HUMAN\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).path("matchId").asText();
        mvc.perform(post("/api/matches/" + id + "/join").contentType("application/json").content("{" + credentials + "}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/matches/" + id)).andExpect(jsonPath("$.players").value(1));
        mvc.perform(post("/api/matches/" + id + "/join").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value("P2"));
    }

    @Test
    void capabilitiesAndInvalidUploadsHaveStructuredErrors() throws Exception {
        mvc.perform(get("/api/bots/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.languages.CPP").value(false));
        mvc.perform(multipart("/api/bots").file(new MockMultipartFile("file", "bot.exe", "application/octet-stream", new byte[]{1})).param("name", "bot"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("UNSUPPORTED_BOT_LANGUAGE"));
    }
}
