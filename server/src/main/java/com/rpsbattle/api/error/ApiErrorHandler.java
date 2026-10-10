package com.rpsbattle.api.error;

import com.rpsbattle.online.DemoRequestException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages="com.rpsbattle.api.controller")
public class ApiErrorHandler {
    @ExceptionHandler(DemoRequestException.class)
    public ResponseEntity<Map<String,String>> domain(DemoRequestException error) {
        var status=error.code().equals("BOT_SANDBOX_UNAVAILABLE")?HttpStatus.SERVICE_UNAVAILABLE:
                error.code().equals("MATCH_NOT_FOUND")||error.code().equals("BOT_NOT_FOUND")?HttpStatus.NOT_FOUND:
                error.code().equals("MATCH_FULL")?HttpStatus.CONFLICT:HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("error",error.code()));
    }
    @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("error","INVALID_MESSAGE"));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,String>> oversized(Exception error){return ResponseEntity.status(413).body(Map.of("error","BOT_FILE_SIZE"));}
}
