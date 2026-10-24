package com.rpsbattle.tournament.coding;

import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.*;
import com.rpsbattle.tournament.*;
import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.timeline.TimelineAccessGuard;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class SubmissionAttemptService {
 private final AccountAuthorizationService accounts;private final UserRepository users;private final TournamentService tournaments;private final OwnedTournamentService owned;private final TimelineAccessGuard access;private final PreuploadedSubmissionService imports;private final SubmissionAttemptStore store;
 public SubmissionAttemptService(AccountAuthorizationService a,UserRepository u,TournamentService t,OwnedTournamentService o,TimelineAccessGuard g,PreuploadedSubmissionService i,SubmissionAttemptStore s){accounts=a;users=u;tournaments=t;owned=o;access=g;imports=i;store=s;}
 private void open(Tournament t,UUID user){if(!t.participantPolicy.bots())throw new AuthException("CODING_NOT_ALLOWED",403);if(t.timeline!=null){access.coding(t,user);return;}Instant now=Instant.now();if(t.status!=TournamentStatus.REGISTRATION||t.botPolicy.submissionOpenAt()!=null&&now.isBefore(t.botPolicy.submissionOpenAt())||t.botPolicy.submissionDeadline()!=null&&!now.isBefore(t.botPolicy.submissionDeadline()))throw new AuthException("WORKSPACE_CLOSED",409);}
 public SubmissionAttemptStore.View latest(Authentication auth,String id){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(t.timeline!=null)access.readable(t,user);return store.latest(UUID.fromString(id),user);}}
 public SubmissionAttemptStore.View start(Authentication auth,String id,UUID requestId,String language,String source,String botId,boolean accepted){var user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){
  if(requestId==null)throw new AuthException("SUBMISSION_REQUEST_REQUIRED",400);if(!accepted)throw new AuthException("RULES_ACCEPTANCE_REQUIRED",400);
  SubmissionAttemptStore.Snapshot snapshot;
  if(botId!=null&&!botId.isBlank()){if(source!=null)throw new AuthException("INVALID_WORKSPACE_SOURCE",400);snapshot=imports.snapshot(auth,t,botId);}
  else{if(t.botPolicy.submissionMode()==BotSubmissionMode.PRE_UPLOADED_ONLY)throw new AuthException("CODING_NOT_ALLOWED",403);if(language==null||!t.botPolicy.allowedLanguages().contains(language))throw new AuthException("BOT_LANGUAGE_NOT_ALLOWED",400);CodeWorkspaceService.validateSource(source,t.botPolicy,language);snapshot=new SubmissionAttemptStore.Snapshot(language,source,null,null);}
  var previous=store.existing(requestId,UUID.fromString(id),user,snapshot);if(previous!=null)return previous;
  open(t,user);return store.start(requestId,t,user,snapshot,access.versionLimit(t,user));
 }}
 public void finish(CodingQueueStore.Lease lease){var user=store.user(lease);var t=tournaments.find(store.tournament(lease).toString());synchronized(t){var account=users.findById(new UserId(user)).filter(a->a.status()==AccountStatus.ACTIVE).orElseThrow(()->new AuthException("ACCOUNT_DISABLED",403));open(t,user);owned.submissionMutation(t,AuthService.sessionAuthentication(account),()->{open(t,user);if(store.finish(lease,t,()->open(t,user)))tournaments.changed(t);});}}
}
