package com.rpsbattle.api.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.worker.remote.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class RemoteResultController {
    private final RemoteResultService results;private final RemoteResultProjector projector;private final ObjectMapper json;
    private final java.util.concurrent.Semaphore uploads=new java.util.concurrent.Semaphore(2);
    public RemoteResultController(RemoteResultService results,RemoteResultProjector projector,ObjectMapper json){this.results=results;this.projector=projector;this.json=json;}
    @PostMapping("/internal/workers/{id}/jobs/{job}/complete")
    public ResponseEntity<?> complete(Authentication auth,@PathVariable UUID id,@PathVariable UUID job,jakarta.servlet.http.HttpServletRequest request)throws java.io.IOException {
        if(auth==null||!id.equals(auth.getPrincipal()))throw new AuthException("WORKER_FORBIDDEN",403);
        if(!uploads.tryAcquire())throw new AuthException("REMOTE_UPLOAD_BUSY",429);
        try {
        if(request.getContentLengthLong()>RemoteResultService.REQUEST_LIMIT)throw new AuthException("REMOTE_RESULT_TOO_LARGE",413);
        byte[] bytes=request.getInputStream().readNBytes(RemoteResultService.REQUEST_LIMIT+1);if(bytes.length>RemoteResultService.REQUEST_LIMIT)throw new AuthException("REMOTE_RESULT_TOO_LARGE",413);
        RemoteResultService.Upload body;try{body=json.readerFor(RemoteResultService.Upload.class).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);}catch(Exception e){throw new AuthException("INVALID_REMOTE_RESULT",400);}
        var receipt=results.accept(id,job,body);
        try{projector.project(receipt.executionId());}catch(RuntimeException ignored){/* Durable receipt remains pending for scheduled projection. */}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt);
        }finally{uploads.release();}
    }
}
