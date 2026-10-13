package com.rpsbattle.auth;

import com.rpsbattle.account.domain.User;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record AccountPrincipal(UUID id,String username,long authVersion) implements Principal, Serializable {
    public static AccountPrincipal from(User user) { return new AccountPrincipal(user.id().value(),user.username(),user.authVersion()); }
    public String getName() { return username; }
}
