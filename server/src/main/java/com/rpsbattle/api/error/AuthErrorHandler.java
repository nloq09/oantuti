package com.rpsbattle.api.error;

import com.rpsbattle.api.controller.AuthController;
import com.rpsbattle.auth.AuthException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes=AuthController.class)
public class AuthErrorHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String,String>> auth(AuthException error) {
        return ResponseEntity.status(error.status()).body(Map.of("error",error.code()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,String>> duplicate(DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(Map.of("error","ACCOUNT_ALREADY_EXISTS"));
    }
}
