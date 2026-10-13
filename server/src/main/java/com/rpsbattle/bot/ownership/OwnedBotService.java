package com.rpsbattle.bot.ownership;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.account.domain.UserRole;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.bot.BotAgent;
import com.rpsbattle.bot.upload.BotUploadService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.*;

@Service
public class OwnedBotService {
    private final BotRepository bots;
    private final AccountAuthorizationService accounts;
    private final BotUploadService artifacts;
    public OwnedBotService(BotRepository bots,AccountAuthorizationService accounts,BotUploadService artifacts) {
        this.bots=bots;this.accounts=accounts;this.artifacts=artifacts;
    }
    public List<BotMetadata> list(Authentication auth) {return bots.owned(accounts.requireUser(auth).id().value());}
    @Transactional public BotMetadata upload(Authentication auth,String name,MultipartFile file) {
        var owner=accounts.requireUser(auth);name=validName(name);
        var upload=artifacts.upload(name,file);
        // Only the newly created artifact can be compensated. Legacy artifacts are never removed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCompletion(int status) {if(status==STATUS_ROLLED_BACK) artifacts.discardNew(upload);}
        });
        return bots.create(metadata(upload,owner.id().value()));
    }
    public BotMetadata require(Authentication auth,String id) {
        accounts.requireUser(auth);
        return authorize(auth,bots.find(uuid(id)).orElseThrow(()->new AuthException("BOT_NOT_FOUND",404)));
    }
    private BotMetadata authorize(Authentication auth,BotMetadata bot) {
        return authorize(auth,bot,false);
    }
    private BotMetadata authorize(Authentication auth,BotMetadata bot,boolean allowDeleted) {
        var actor=accounts.requireUser(auth);
        if(!actor.id().value().equals(bot.ownerUserId()) && actor.role()!=UserRole.ADMIN) throw new AuthException("BOT_ACCESS_DENIED",403);
        if(!allowDeleted && bot.status()==BotMetadata.Status.DELETED) throw new AuthException("BOT_NOT_FOUND",404);
        return bot;
    }
    @Transactional public BotMetadata rename(Authentication auth,String id,String name) {
        var bot=authorize(auth,bots.lock(uuid(id)).orElseThrow(()->new AuthException("BOT_NOT_FOUND",404)));
        return bots.save(bot.rename(validName(name)));
    }
    @Transactional public void delete(Authentication auth,String id) {
        var bot=authorize(auth,bots.lock(uuid(id)).orElseThrow(()->new AuthException("BOT_NOT_FOUND",404)));
        bots.save(bot.deleted());
    }
    /** Internal capability is retrieved on the server, never accepted from clients as authority. */
    public BotUploadService.Upload execution(Authentication auth,String id) {
        var bot=require(auth,id);var stored=artifacts.stored(id);
        return new BotUploadService.Upload(id,bot.name(),bot.language(),stored.token());
    }
    public BotAgent agent(Authentication auth,String id) {var bot=execution(auth,id);return artifacts.agent(id,bot.token());}
    public BotMetadata authorizeWithdrawal(Authentication auth,String id) {
        accounts.requireUser(auth);
        return authorize(auth,bots.find(uuid(id)).orElseThrow(()->new AuthException("BOT_NOT_FOUND",404)),true);
    }
    static UUID uuid(String id) {
        try {var value=UUID.fromString(id);if(!value.toString().equals(id)) throw new IllegalArgumentException();return value;}
        catch(RuntimeException error) {throw new AuthException("BOT_NOT_FOUND",404);}
    }
    static String validName(String name) {
        if(name==null || name.strip().isBlank() || name.strip().length()>60 || name.chars().anyMatch(Character::isISOControl)) throw new AuthException("INVALID_BOT_NAME",400);
        return name.strip();
    }
    static BotMetadata metadata(BotUploadService.Upload upload,UUID owner) {
        var now=Instant.now();return new BotMetadata(uuid(upload.id()),owner,validName(upload.name()),upload.language(),upload.id()+"/"+upload.language().filename,BotMetadata.Status.UPLOADED,now,now);
    }
}
