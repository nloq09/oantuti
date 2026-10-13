package com.rpsbattle.account.domain;

import java.time.Instant;

public record User(UserId id, String username, String email, String passwordHash,
                   String displayName, UserRole role, AccountStatus status,
                   Instant createdAt, Instant updatedAt, Instant lastLoginAt, long authVersion) {
    public User(UserId id,String username,String email,String passwordHash,String displayName,UserRole role,
                AccountStatus status,Instant createdAt,Instant updatedAt,Instant lastLoginAt) {
        this(id,username,email,passwordHash,displayName,role,status,createdAt,updatedAt,lastLoginAt,0);
    }
    public User withLastLogin(Instant now) {
        return new User(id, username, email, passwordHash, displayName, role, status, createdAt, now, now, authVersion);
    }
    public User withDisplayName(String name,Instant now) { return new User(id,username,email,passwordHash,name,role,status,createdAt,now,lastLoginAt,authVersion); }
    public User withPasswordHash(String hash,Instant now) { return new User(id,username,email,hash,displayName,role,status,createdAt,now,lastLoginAt,Math.addExact(authVersion,1)); }
    public User withStatus(AccountStatus next,Instant now) { return new User(id,username,email,passwordHash,displayName,role,next,createdAt,now,lastLoginAt,Math.addExact(authVersion,1)); }
    @Override public String toString() { return "User[id=" + id + ", role=" + role + ", status=" + status + "]"; }
}
