package com.rpsbattle.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class PasswordHashTest {
    @Test void equalPasswordsGetDifferentSaltsAndWrongPasswordsDoNotMatch() {
        var encoder=new BCryptPasswordEncoder(12);var password="A long test password";
        String first=encoder.encode(password), second=encoder.encode(password);
        assertThat(first).isNotEqualTo(second).isNotEqualTo(password);
        assertThat(encoder.matches(password,first)).isTrue();assertThat(encoder.matches("wrong password",first)).isFalse();
    }
    @Test void passwordPolicyChecksUtf8BytesWithoutTrimmingThePassword() {
        assertThat(RegistrationService.validPassword("x".repeat(11))).isFalse();
        assertThat(RegistrationService.validPassword("x".repeat(12))).isTrue();
        assertThat(RegistrationService.validPassword("x".repeat(72))).isTrue();
        assertThat(RegistrationService.validPassword("x".repeat(73))).isFalse();
        assertThat(RegistrationService.validPassword("é".repeat(36))).isTrue();
        assertThat(RegistrationService.validPassword("é".repeat(37))).isFalse();
        assertThat(RegistrationService.validPassword(null)).isFalse();
        assertThat(RegistrationService.validPassword(" ".repeat(12))).isFalse();
    }
}
