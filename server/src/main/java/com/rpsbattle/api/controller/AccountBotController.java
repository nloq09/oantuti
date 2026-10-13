package com.rpsbattle.api.controller;
import com.rpsbattle.bot.ownership.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/account/bots")
public class AccountBotController {
    private final OwnedBotService bots;private final LegacyBotImportService legacy;
    public AccountBotController(OwnedBotService bots,LegacyBotImportService legacy) {this.bots=bots;this.legacy=legacy;}
    @GetMapping public List<View> list(Authentication auth) {return bots.list(auth).stream().map(View::of).toList();}
    @GetMapping("/{id}") public View detail(Authentication auth,@PathVariable String id) {return View.of(bots.require(auth,id));}
    @PatchMapping("/{id}") public View rename(Authentication auth,@PathVariable String id,@RequestBody Rename body) {return View.of(bots.rename(auth,id,body.name()));}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth,@PathVariable String id) {bots.delete(auth,id);}
    @PostMapping("/import") public View claim(Authentication auth,@RequestBody Claim body) {return View.of(legacy.claim(auth,body.botId(),body.botToken()));}
    public record Rename(String name) {}
    public record Claim(String botId,String botToken) {
        @Override public String toString() {return "Claim[botId="+botId+", botToken=[redacted]]";}
    }
    public record View(UUID id,String name,String language,String status,Instant createdAt,Instant updatedAt) {
        public static View of(BotMetadata b) {return new View(b.id(),b.name(),b.language().name(),b.status().name(),b.createdAt(),b.updatedAt());}
    }
}
