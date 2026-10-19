package com.rpsbattle.tournament.coding;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.bot.ownership.OwnedBotService;
import com.rpsbattle.bot.upload.*;
import com.rpsbattle.tournament.TournamentService;
import com.rpsbattle.tournament.domain.*;
import jakarta.persistence.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
@Service
public class PreuploadedSubmissionService {
 private final AccountAuthorizationService accounts;private final OwnedBotService bots;private final BotUploadService uploads;private final WorkspaceStore store;private final SubmissionStore submissions;private final TournamentService tournaments;
 @PersistenceContext EntityManager db;
 @org.springframework.beans.factory.annotation.Autowired CodingQueueStore queue;
 @org.springframework.beans.factory.annotation.Autowired com.rpsbattle.tournament.timeline.TimelineAccessGuard timelineAccess;
 public PreuploadedSubmissionService(AccountAuthorizationService a,OwnedBotService b,BotUploadService u,WorkspaceStore w,SubmissionStore s,TournamentService t){accounts=a;bots=b;uploads=u;store=w;submissions=s;tournaments=t;}
 @Transactional public WorkspaceStore.View importBot(Authentication auth,String id,String botId){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(t.timeline!=null)timelineAccess.coding(t,user);
  if(t.status!=TournamentStatus.REGISTRATION||!t.participantPolicy.bots())throw new AuthException("WORKSPACE_CLOSED",409);Instant now=Instant.now();if(t.botPolicy.submissionOpenAt()!=null&&now.isBefore(t.botPolicy.submissionOpenAt())||t.botPolicy.submissionDeadline()!=null&&!now.isBefore(t.botPolicy.submissionDeadline()))throw new AuthException("WORKSPACE_CLOSED",409);
  var meta=bots.require(auth,botId);if(!user.equals(meta.ownerUserId()))throw new AuthException("BOT_ACCESS_DENIED",403);t.botPolicy.requirePreuploaded(meta.language());
  var source=uploads.authorize(botId,bots.execution(auth,botId).token());byte[] bytes;try(var input=Files.newInputStream(uploads.source(source))){bytes=input.readNBytes(meta.language()==BotLanguage.JAR?5*1024*1024+1:t.botPolicy.maxSourceBytes()+1);}catch(java.io.IOException error){throw new AuthException("BOT_ARTIFACT_CHANGED",409);}
  if(bytes.length==0||bytes.length>(meta.language()==BotLanguage.JAR?5*1024*1024:t.botPolicy.maxSourceBytes()))throw new AuthException("INVALID_WORKSPACE_SOURCE",400);
  String language=meta.language()==BotLanguage.JAR?"JAVA":meta.language().name(),text=meta.language()==BotLanguage.JAR?Base64.getEncoder().encodeToString(bytes):new String(bytes,StandardCharsets.UTF_8);
  if(meta.language()!=BotLanguage.JAR)CodeWorkspaceService.validateSource(text,t.botPolicy,language);
  queue.admit();submissions.enable(id);var w=store.create(UUID.fromString(id),user,language,text);w=store.save(UUID.fromString(id),user,language,w.revision(),text);var work=store.claim(UUID.fromString(id),user,language,w.revision(),text,t.botPolicy.maxSubmissionVersions(),"BUILD");var version=db.find(CodeVersion.class,work.versionId());version.importedBotId=meta.id();version.runtimeLanguage=meta.language().name();return store.read(UUID.fromString(id),user,language);
 }}
}
