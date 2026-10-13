package com.rpsbattle.account.persistence;

import com.rpsbattle.account.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id private UUID id;
    @Column(nullable=false, length=32) private String username;
    @Column(nullable=false, length=254) private String email;
    @Column(name="password_hash", nullable=false, length=255) private String passwordHash;
    @Column(name="display_name", nullable=false, length=60) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private UserRole role;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private AccountStatus status;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Column(name="last_login_at") private Instant lastLoginAt;
    protected UserEntity() {}
    static UserEntity from(User user) {
        var entity=new UserEntity();
        entity.id=user.id().value();entity.username=user.username();entity.email=user.email();
        entity.passwordHash=user.passwordHash();entity.displayName=user.displayName();entity.role=user.role();
        entity.status=user.status();entity.createdAt=user.createdAt();entity.updatedAt=user.updatedAt();entity.lastLoginAt=user.lastLoginAt();
        return entity;
    }
    User toDomain() { return new User(new UserId(id),username,email,passwordHash,displayName,role,status,createdAt,updatedAt,lastLoginAt); }
}
