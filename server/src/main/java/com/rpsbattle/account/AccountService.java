package com.rpsbattle.account;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class AccountService {
    private final UserRepository users;
    private final AccountAuthorizationService authorization;
    private final PasswordEncoder passwords;
    public AccountService(UserRepository users,AccountAuthorizationService authorization,PasswordEncoder passwords) {
        this.users=users;this.authorization=authorization;this.passwords=passwords;
    }
    public User profile(Authentication authentication) {return authorization.requireUser(authentication);}
    private User locked(Authentication authentication) {
        var current=authorization.requireUser(authentication);
        var user=users.findByIdForUpdate(current.id()).orElseThrow(()->new AuthException("UNAUTHENTICATED",401));
        if(user.status()!=AccountStatus.ACTIVE || user.authVersion()!=current.authVersion()) throw new AuthException("UNAUTHENTICATED",401);
        return user;
    }
    @Transactional public User updateProfile(Authentication authentication,String displayName) {
        var user=locked(authentication);var name=displayName==null?"":displayName.strip();
        if(name.isEmpty() || name.length()>60 || name.chars().anyMatch(Character::isISOControl)) throw new AuthException("INVALID_DISPLAY_NAME",400);
        return users.save(user.withDisplayName(name,Instant.now()));
    }
    @Transactional public void changePassword(Authentication authentication,String currentPassword,String nextPassword) {
        var user=locked(authentication);
        if(!RegistrationService.validPassword(currentPassword) || !passwords.matches(currentPassword,user.passwordHash()))
            throw new AuthException("CURRENT_PASSWORD_INVALID",400);
        if(!RegistrationService.validPassword(nextPassword)) throw new AuthException("INVALID_PASSWORD",400);
        if(passwords.matches(nextPassword,user.passwordHash())) throw new AuthException("PASSWORD_UNCHANGED",400);
        users.save(user.withPasswordHash(passwords.encode(nextPassword),Instant.now()));
    }
}
