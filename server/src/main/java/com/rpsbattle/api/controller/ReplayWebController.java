package com.rpsbattle.api.controller;
import com.rpsbattle.replay.ReplayRegistry;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class ReplayWebController {
    private final ReplayRegistry replays;
    public ReplayWebController(ReplayRegistry replays){this.replays=replays;}
    @GetMapping("/api/matches/{id}/replay") public Map<String,Object> replay(@PathVariable String id){return replays.view(id);}
}
