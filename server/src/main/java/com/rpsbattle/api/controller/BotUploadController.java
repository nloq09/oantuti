package com.rpsbattle.api.controller;

import com.rpsbattle.bot.upload.BotUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/bots")
public class BotUploadController {
    private final BotUploadService service;
    private final com.rpsbattle.bot.ownership.OwnedBotService owned;

    public BotUploadController(BotUploadService service, com.rpsbattle.bot.ownership.OwnedBotService owned) {
        this.service = service;
        this.owned = owned;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return service.capabilities();
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountBotController.View upload(org.springframework.security.core.Authentication auth,@RequestParam String name, @RequestParam MultipartFile file) {
        return AccountBotController.View.of(owned.upload(auth,name, file));
    }
}
