package com.rpsbattle.auth;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public RegistrationService(UserRepository users, PasswordEncoder passwords) { this.users=users;this.passwords=passwords; }
    @Transactional
    public User register(String username, String email, String password, String displayName) {
        String name=normalize(username), address=normalize(email), display=displayName==null ? "" : displayName.strip();
        if (!name.matches("[a-z0-9_]{3,32}")) throw new AuthException("INVALID_USERNAME",400);
        if (address.length()>254 || !address.matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")) throw new AuthException("INVALID_EMAIL",400);
        if (display.isEmpty() || display.length()>60 || display.chars().anyMatch(Character::isISOControl)) throw new AuthException("INVALID_DISPLAY_NAME",400);
        if (!validPassword(password)) throw new AuthException("INVALID_PASSWORD",400);
        if (users.existsByUsername(name) || users.existsByEmail(address)) throw new AuthException("ACCOUNT_ALREADY_EXISTS",409);
        var now=Instant.now();
        return users.save(new User(UserId.random(),name,address,passwords.encode(password),display,UserRole.USER,AccountStatus.ACTIVE,now,now,null));
    }
    public static boolean validPassword(String password) {
        return password!=null && !password.isBlank() && password.codePointCount(0,password.length())>=12
                && password.getBytes(StandardCharsets.UTF_8).length<=72;
    }
    private String normalize(String value) { return value==null ? "" : value.strip().toLowerCase(Locale.ROOT); }
}
