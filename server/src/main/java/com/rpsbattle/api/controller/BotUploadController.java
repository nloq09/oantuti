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
    public BotUploadController(BotUploadService service){this.service=service;}
    @GetMapping("/capabilities")public Map<String,Object> capabilities(){return service.capabilities();}
    @PostMapping(consumes="multipart/form-data")@ResponseStatus(HttpStatus.CREATED)
    public BotUploadService.Upload upload(@RequestParam String name,@RequestParam MultipartFile file){return service.upload(name,file);}
}
