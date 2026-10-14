package com.rpsbattle.api.controller;
import com.rpsbattle.admin.AdminMatchReadService;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/admin/matches")
public class AdminMatchesController {
    private final AdminMatchReadService service;
    public AdminMatchesController(AdminMatchReadService service){this.service=service;}
    @GetMapping public ResponseEntity<?> list(Authentication auth,@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(auth,filters,page,size));}
    @GetMapping("/{id}") public ResponseEntity<?> detail(Authentication auth,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.detail(auth,id));}
}
