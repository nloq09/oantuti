package com.rpsbattle.bot.ownership;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.bot.upload.BotUploadService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegacyBotImportService {
    private final BotRepository bots;private final UserRepository users;
    private final AccountAuthorizationService accounts;private final BotUploadService artifacts;
    public LegacyBotImportService(BotRepository bots,UserRepository users,AccountAuthorizationService accounts,BotUploadService artifacts) {
        this.bots=bots;this.users=users;this.accounts=accounts;this.artifacts=artifacts;
    }
    @Transactional public BotMetadata claim(Authentication auth,String id,String token) {
        var owner=accounts.requireUser(auth);var uuid=OwnedBotService.uuid(id);
        // Serialize claims by account, and rely on the bot PK for simultaneous claims by different accounts.
        users.findByIdForUpdate(owner.id()).orElseThrow(()->new AuthException("UNAUTHENTICATED",401));
        var existing=bots.find(uuid);
        if(existing.isPresent()) {
            if(!existing.get().ownerUserId().equals(owner.id().value())) throw new AuthException("BOT_ALREADY_OWNED",409);
            if(existing.get().status()==BotMetadata.Status.DELETED) throw new AuthException("BOT_NOT_FOUND",404);
            artifacts.authorize(id,token);return existing.get();
        }
        var upload=artifacts.authorize(id,token);
        if(!java.nio.file.Files.isRegularFile(artifacts.source(upload))) throw new AuthException("BOT_ARTIFACT_MISSING",409);
        return bots.create(OwnedBotService.metadata(upload,owner.id().value()));
    }
}
