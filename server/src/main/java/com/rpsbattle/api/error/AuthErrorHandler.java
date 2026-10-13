package com.rpsbattle.api.error;

import com.rpsbattle.api.controller.AuthController;
import com.rpsbattle.api.controller.AccountController;
import com.rpsbattle.api.controller.AdminController;
import com.rpsbattle.api.controller.AccountBotController;
import com.rpsbattle.api.controller.BotUploadController;
import com.rpsbattle.api.controller.MatchController;
import com.rpsbattle.api.controller.TournamentController;
import com.rpsbattle.api.controller.AccountHistoryController;
import com.rpsbattle.auth.AuthException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes={AuthController.class,AccountController.class,AdminController.class,AccountBotController.class,BotUploadController.class,MatchController.class,TournamentController.class,AccountHistoryController.class})
public class AuthErrorHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String,String>> auth(AuthException error) {
        return ResponseEntity.status(error.status()).body(Map.of("error",error.code()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,String>> duplicate(DataIntegrityViolationException error,jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(409).body(Map.of("error",request.getServletPath().startsWith("/api/auth/") ? "ACCOUNT_ALREADY_EXISTS" : "RESOURCE_CONFLICT"));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Map<String,String>> database(org.springframework.dao.DataAccessException error) {
        return ResponseEntity.status(503).body(Map.of("error","DATABASE_UNAVAILABLE"));
    }
}
