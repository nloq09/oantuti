package com.rpsbattle.admin;
import com.rpsbattle.auth.AuthException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class BotAdministrationPolicy {
    private final JdbcTemplate db;
    public BotAdministrationPolicy(JdbcTemplate db){this.db=db;}
    /** Existing frozen tournament entries remain reproducible; new seats/registrations are blocked. */
    public void requireEnabled(String id){
        var flags=db.queryForList("select admin_disabled from bots where id=?",Boolean.class,UUID.fromString(id));
        if(!flags.isEmpty()&&Boolean.TRUE.equals(flags.getFirst()))throw new AuthException("BOT_DISABLED",409);
    }
}
