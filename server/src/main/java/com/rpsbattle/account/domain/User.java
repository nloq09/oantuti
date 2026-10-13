package com.rpsbattle.account.domain;

import java.time.Instant;

public record User(UserId id, String username, String email, String passwordHash,
                   String displayName, UserRole role, AccountStatus status,
                   Instant createdAt, Instant updatedAt, Instant lastLoginAt) {
    public User withLastLogin(Instant now) {
        return new User(id, username, email, passwordHash, displayName, role, status, createdAt, now, now);
    }
    @Override public String toString() { return "User[id=" + id + ", role=" + role + ", status=" + status + "]"; }
}
