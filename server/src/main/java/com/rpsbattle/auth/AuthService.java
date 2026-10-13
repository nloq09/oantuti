package com.rpsbattle.auth;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.List;

@Service
public class AuthService {
    private final UserRepository users;
    private final AuthenticationManager authenticationManager;
    public AuthService(UserRepository users, AuthenticationManager authenticationManager) {
        this.users=users;this.authenticationManager=authenticationManager;
    }
    public Authentication authenticate(String username, String password) {
        String normalized=username==null ? "" : username.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{3,32}") || !RegistrationService.validPassword(password))
            throw new AuthException("INVALID_CREDENTIALS",401);
        var before=users.findByUsername(normalized);
        try {
            var authenticated=authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(normalized,password));
            if(authenticated instanceof UsernamePasswordAuthenticationToken token && before.isPresent()) token.setDetails(AccountPrincipal.from(before.get()));
            return authenticated;
        }
        catch (AuthenticationException error) { throw new AuthException("INVALID_CREDENTIALS",401); }
    }
    public Optional<User> current(Authentication authentication) {
        if (authentication==null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) return Optional.empty();
        if(authentication.getPrincipal() instanceof AccountPrincipal principal)
            return users.findById(new UserId(principal.id())).filter(user -> user.status()==AccountStatus.ACTIVE && user.authVersion()==principal.authVersion());
        return users.findByUsername(authentication.getName()).filter(user -> user.status()==AccountStatus.ACTIVE);
    }
    @Transactional
    public User markLogin(Authentication authentication) {
        var snapshot=current(authentication).orElseThrow(()->new AuthException("INVALID_CREDENTIALS",401));
        var user=users.findByIdForUpdate(snapshot.id()).orElseThrow(()->new AuthException("INVALID_CREDENTIALS",401));
        if(user.status()!=AccountStatus.ACTIVE || !(authentication.getDetails() instanceof AccountPrincipal version)
                || version.authVersion()!=user.authVersion()) throw new AuthException("INVALID_CREDENTIALS",401);
        return users.save(user.withLastLogin(Instant.now()));
    }
    public static Authentication sessionAuthentication(User user) {
        return UsernamePasswordAuthenticationToken.authenticated(AccountPrincipal.from(user),null,
                List.of(new SimpleGrantedAuthority("ROLE_"+user.role().name())));
    }
}
