package com.rpsbattle.bot.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import com.rpsbattle.auth.*;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.bot.ownership.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired BotRepository bots;
    @Autowired BotUploadService artifacts;
    @Autowired com.rpsbattle.tournament.TournamentService tournaments;
    MockHttpSession session;
    User owner;
    MockHttpSession account(User user) {
        var session=new MockHttpSession();
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(AuthService.sessionAuthentication(user));
        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        return session;
    }
    User register() {var name="bot_"+UUID.randomUUID().toString().substring(0,8);return registration.register(name,name+"@example.com","Correct Bot Password","Player");}
    @BeforeEach void login() {owner=register();session=account(owner);}

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("rps.bots.directory", root::toString);
    }

    @Test
    void multipartUploadReturnsOwnedMetadataAndMissingRunnerDoesNotReserveASeat() throws Exception {
        var result = mvc.perform(uploadRequest(session,true).file(new MockMultipartFile("file", "bot.py", "text/plain", "print(1)".getBytes())).param("name", "Python bot"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.language").value("PYTHON"))
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.path").doesNotExist()).andReturn();
        var bot = json.readTree(result.getResponse().getContentAsString());
        String credentials = "\"botId\":\"" + bot.path("id").asText() + "\",\"botToken\":\"" + bot.path("token").asText() + "\"";
        mvc.perform(post("/api/matches").session(session).with(csrf()).contentType("application/json").content("{\"mode\":\"HUMAN_VS_HUMAN\"," + credentials + "}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("BOT_SANDBOX_UNAVAILABLE"));
        var created = mvc.perform(post("/api/matches").session(session).with(csrf()).contentType("application/json").content("{\"mode\":\"HUMAN_VS_HUMAN\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).path("matchId").asText();
        mvc.perform(post("/api/matches/" + id + "/join").session(session).with(csrf()).contentType("application/json").content("{" + credentials + "}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/matches/" + id)).andExpect(jsonPath("$.players").value(1));
        mvc.perform(post("/api/matches/" + id + "/join").session(session).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value("P2"));
    }

    @Test
    void capabilitiesAndInvalidUploadsHaveStructuredErrors() throws Exception {
        mvc.perform(get("/api/bots/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.languages.CPP").value(false));
        mvc.perform(uploadRequest(session,true).file(new MockMultipartFile("file", "bot.exe", "application/octet-stream", new byte[]{1})).param("name", "bot"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("UNSUPPORTED_BOT_LANGUAGE"));
    }
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder uploadRequest(MockHttpSession session,boolean token) {
        var request=multipart("/api/bots");if(session!=null) request.session(session);if(token) request.with(csrf());return request;
    }
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder invalidUploadRequest() {
        var request=uploadRequest(session,false);request.with(csrf().useInvalidToken());return request;
    }
    String uploadOwned() throws Exception {
        var result=mvc.perform(uploadRequest(session,true)
            .file(new MockMultipartFile("file","bot.py","text/plain","print(1)".getBytes())).param("name","Owned"))
            .andExpect(status().isCreated()).andReturn();
        var dto=json.readTree(result.getResponse().getContentAsString());
        assertThat(dto.toString()).doesNotContain("token","sourcePath","ownerUserId","command");
        return dto.path("id").asText();
    }
    @Test void ownershipListRenameDeleteAndAdminAreEnforced() throws Exception {
        var id=uploadOwned();var other=account(register());
        mvc.perform(get("/api/account/bots").session(other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/account/bots").session(session)).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/account/bots/"+id).session(other)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/account/bots/"+id).session(other).with(csrf()).contentType("application/json").content("{\"name\":\"Stolen\"}" )).andExpect(status().isForbidden());
        mvc.perform(delete("/api/account/bots/"+id).session(other).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/account/bots/"+id).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("name"," Renamed ","ownerUserId","spoof","status","READY")))).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.status").value("UPLOADED"));
        var administrator=register();administrator=users.save(new User(administrator.id(),administrator.username(),administrator.email(),administrator.passwordHash(),administrator.displayName(),UserRole.ADMIN,administrator.status(),administrator.createdAt(),administrator.updatedAt(),null));
        mvc.perform(get("/api/account/bots/"+id).session(account(administrator))).andExpect(status().isOk());
        var stored=artifacts.stored(id);var cup=tournaments.create("Preserve",com.rpsbattle.tournament.domain.TournamentFormat.ROUND_ROBIN);
        tournaments.add(cup.id,id,stored.token(),"Snapshot name",42L);
        mvc.perform(delete("/api/account/bots/"+id).session(account(administrator)).with(csrf())).andExpect(status().isNoContent());
        assertThat(bots.find(UUID.fromString(id)).orElseThrow().status()).isEqualTo(BotMetadata.Status.DELETED);
        assertThat(java.nio.file.Files.exists(artifacts.source(stored))).isTrue();
        assertThat(cup.participants).hasSize(1);assertThat(artifacts.authorize(id,stored.token())).isNotNull();
        mvc.perform(get("/api/account/bots/"+id).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/account/bots").session(session)).andExpect(jsonPath("$.length()").value(0));
        // Withdrawal is authorized by DB ownership even after soft deletion and loss of runner metadata.
        java.nio.file.Files.delete(artifacts.source(stored).getParent().resolve("metadata.json"));
        mvc.perform(delete("/api/tournaments/"+cup.id+"/participants/"+cup.participants.getFirst().participantId()).session(session).with(csrf())).andExpect(status().isOk());
        assertThat(cup.participants).isEmpty();
    }
    @Test void stolenTokensCannotUseBotsInMatchesOrTournaments() throws Exception {
        var id=uploadOwned();var token=artifacts.stored(id).token();var stranger=account(register());
        String body=json.writeValueAsString(Map.of("mode","HUMAN_VS_BOT","botId",id,"botToken",token));
        mvc.perform(post("/api/matches").session(stranger).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/matches").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/matches").session(session).with(csrf().useInvalidToken()).contentType("application/json").content(body)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("CSRF_INVALID"));
        var room=mvc.perform(post("/api/matches").contentType("application/json").content("{\"mode\":\"HUMAN_VS_HUMAN\"}")).andExpect(status().isCreated()).andReturn();
        var roomId=json.readTree(room.getResponse().getContentAsString()).path("matchId").asText();
        mvc.perform(post("/api/matches/"+roomId+"/join").session(stranger).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/matches/"+roomId)).andExpect(jsonPath("$.players").value(1));
        var cup=tournaments.create("Ownership",com.rpsbattle.tournament.domain.TournamentFormat.ROUND_ROBIN);
        mvc.perform(post("/api/tournaments/"+cup.id+"/participants").session(stranger).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/tournaments/"+cup.id+"/participants").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        var participant=cup.participants.getFirst().participantId();
        mvc.perform(delete("/api/tournaments/"+cup.id+"/participants/"+participant).session(stranger).with(csrf()).header("X-Bot-Token",token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/tournaments/"+cup.id+"/participants/"+participant).session(session).with(csrf())).andExpect(status().isOk());
    }
    @Test void legacyImportRequiresProofAndNeverTransfersOwnershipOrRestoresDeletedBots() throws Exception {
        var legacy=artifacts.upload("Legacy",new MockMultipartFile("file","bot.cpp","text/plain","int main(){}".getBytes()));
        var path="/api/account/bots/import";
        mvc.perform(post(path).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("botId",legacy.id(),"botToken","wrong")))).andExpect(status().isBadRequest());
        assertThat(bots.find(UUID.fromString(legacy.id()))).isEmpty();
        var body=json.writeValueAsString(Map.of("botId",legacy.id(),"botToken",legacy.token()));
        mvc.perform(post(path).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist());
        mvc.perform(post(path).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post(path).session(account(register())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(delete("/api/account/bots/"+legacy.id()).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post(path).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
        assertThat(java.nio.file.Files.exists(artifacts.source(legacy))).isTrue();
    }
    @Test void anonymousUploadsAndAccountMutationsRequireSessionAndCsrf() throws Exception {
        mvc.perform(get("/api/account/bots")).andExpect(status().isUnauthorized());
        mvc.perform(uploadRequest(null,true).file(new MockMultipartFile("file","bot.py","text/plain","print(1)".getBytes())).param("name","guest")).andExpect(status().isUnauthorized());
        mvc.perform(invalidUploadRequest().file(new MockMultipartFile("file","bot.py","text/plain","print(1)".getBytes())).param("name","guest")).andExpect(status().isForbidden());
        var id=uploadOwned();
        mvc.perform(delete("/api/account/bots/"+id).session(session).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
    }

}
