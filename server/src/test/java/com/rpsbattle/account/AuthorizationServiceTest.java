package com.rpsbattle.account;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class AuthorizationServiceTest {
    @Autowired RegistrationService registration;
    @Autowired AccountAuthorizationService authorization;
    @Test void applicationAuthorizationDoesNotTrustClaimedAdminAuthority() {
        String name="claims_"+UUID.randomUUID().toString().substring(0,8);
        var user=registration.register(name,name+"@example.com","Correct Password 123","Player");
        var claimed=UsernamePasswordAuthenticationToken.authenticated(AccountPrincipal.from(user),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(authorization.requireUser(claimed).id()).isEqualTo(user.id());
        assertThatThrownBy(()->authorization.requireAdmin(claimed)).isInstanceOf(AuthException.class).hasMessage("FORBIDDEN");
        var stale=UsernamePasswordAuthenticationToken.authenticated(new AccountPrincipal(user.id().value(),user.username(),user.authVersion()+1),null,List.of());
        assertThatThrownBy(()->authorization.requireUser(stale)).hasMessage("UNAUTHENTICATED");
    }
}
