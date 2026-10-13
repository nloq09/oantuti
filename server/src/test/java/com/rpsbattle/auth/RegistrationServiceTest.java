package com.rpsbattle.auth;

import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class RegistrationServiceTest {
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Test void registersNormalizedAccountWithSaltedPasswordAndSafeDefaults() {
        String name="reg_"+UUID.randomUUID().toString().substring(0,8), password="TwelveCharsOrMore123";
        var user=registration.register(" "+name.toUpperCase()+" "," "+name.toUpperCase()+"@EXAMPLE.COM ",password," Alice ");
        assertThat(user.username()).isEqualTo(name);assertThat(user.email()).isEqualTo(name+"@example.com");
        assertThat(user.displayName()).isEqualTo("Alice");assertThat(user.role()).isEqualTo(UserRole.USER);
        assertThat(user.status()).isEqualTo(AccountStatus.ACTIVE);assertThat(user.lastLoginAt()).isNull();
        assertThat(user.passwordHash()).startsWith("$2a$12$").isNotEqualTo(password);
        assertThat(passwords.matches(password,users.findById(user.id()).orElseThrow().passwordHash())).isTrue();
        assertThatThrownBy(()->registration.register(name.toUpperCase(),"other@example.com",password,"Other"))
                .isInstanceOf(AuthException.class).hasMessage("ACCOUNT_ALREADY_EXISTS");
        assertThatThrownBy(()->registration.register("another_"+UUID.randomUUID().toString().substring(0,8),user.email().toUpperCase(),password,"Other"))
                .isInstanceOf(AuthException.class).hasMessage("ACCOUNT_ALREADY_EXISTS");
    }
    @ParameterizedTest
    @CsvSource({"ab,a@example.com,LongPassword123,Alice,INVALID_USERNAME",
            "user!,a@example.com,LongPassword123,Alice,INVALID_USERNAME",
            "valid_user,not-an-email,LongPassword123,Alice,INVALID_EMAIL",
            "valid_user,a@example.com,short,Alice,INVALID_PASSWORD"})
    void rejectsInvalidInputBeforeSaving(String username,String email,String password,String display,String error) {
        assertThatThrownBy(()->registration.register(username,email,password,display)).isInstanceOf(AuthException.class).hasMessage(error);
    }
    @Test void rejectsNullsBlankDisplayAndOversizedUtf8Passwords() {
        assertThatThrownBy(()->registration.register(null,null,null,null)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->registration.register("valid_user","a@example.com","LongPassword123"," "))
                .hasMessage("INVALID_DISPLAY_NAME");
        assertThatThrownBy(()->registration.register("valid_user","a@example.com","é".repeat(37),"Alice"))
                .hasMessage("INVALID_PASSWORD");
    }
}
