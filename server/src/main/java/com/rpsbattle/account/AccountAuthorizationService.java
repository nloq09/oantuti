package com.rpsbattle.account;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.auth.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AccountAuthorizationService {
    private final AuthService auth;
    public AccountAuthorizationService(AuthService auth) {this.auth=auth;}
    public User requireUser(Authentication authentication) {
        return auth.current(authentication).orElseThrow(()->new AuthException("UNAUTHENTICATED",401));
    }
    public User requireAdmin(Authentication authentication) {
        var user=requireUser(authentication);
        if(user.role()!=UserRole.ADMIN) throw new AuthException("FORBIDDEN",403);
        return user;
    }
}
