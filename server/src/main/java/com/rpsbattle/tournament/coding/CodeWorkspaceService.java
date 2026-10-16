package com.rpsbattle.tournament.coding;
import com.rpsbattle.codevalidation.BotTestService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.account.AccountAuthorizationService;
import com.rpsbattle.auth.AuthException;
import com.rpsbattle.online.DemoRequestException;
import com.rpsbattle.tournament.TournamentService;
import com.rpsbattle.tournament.domain.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class CodeWorkspaceService {
 private final AccountAuthorizationService accounts;private final TournamentService tournaments;private final WorkspaceStore store;private final WorkspaceSandbox sandbox;private final BotTestService tests;private final ObjectMapper json;
 @org.springframework.beans.factory.annotation.Autowired SubmissionStore submissions;
 public CodeWorkspaceService(AccountAuthorizationService accounts,TournamentService tournaments,WorkspaceStore store,WorkspaceSandbox sandbox,BotTestService tests,ObjectMapper json){this.accounts=accounts;this.tournaments=tournaments;this.store=store;this.sandbox=sandbox;this.tests=tests;this.json=json;}
 private String language(Tournament t,String value){String l=value==null?t.botPolicy.allowedLanguages().stream().sorted().findFirst().orElseThrow():value;if(!Set.of("PYTHON","CPP","JAVA").contains(l)||!t.botPolicy.allowedLanguages().contains(l))throw new DemoRequestException("BOT_LANGUAGE_NOT_ALLOWED");return l;}
 private void mode(Tournament t){if(t.participantPolicy==ParticipantPolicy.HUMAN_ONLY||t.botPolicy.submissionMode()==BotSubmissionMode.PRE_UPLOADED_ONLY)throw new AuthException("CODING_NOT_ALLOWED",403);}
 private void writable(Tournament t){mode(t);Instant now=Instant.now();if(t.status!=TournamentStatus.REGISTRATION||t.botPolicy.submissionOpenAt()!=null&&now.isBefore(t.botPolicy.submissionOpenAt())||t.botPolicy.submissionDeadline()!=null&&!now.isBefore(t.botPolicy.submissionDeadline()))throw new AuthException("WORKSPACE_CLOSED",409);}
 public static void validateSource(String source,TournamentBotPolicy policy,String language){
  if(source==null||source.isBlank()||source.getBytes(StandardCharsets.UTF_8).length>policy.maxSourceBytes()||!new String(source.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8).equals(source)||source.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new DemoRequestException("INVALID_WORKSPACE_SOURCE");
  if(language.equals("JAVA")&&(java.util.regex.Pattern.compile("(?m)^\\s*package\\s+").matcher(source).find()||!java.util.regex.Pattern.compile("\\bclass\\s+Main\\b").matcher(source).find()))throw new DemoRequestException("JAVA_MAIN_REQUIRED");
 }
 public String template(String language){try{return new ClassPathResource("code-templates/"+switch(language){case "PYTHON"->"main.py";case "CPP"->"main.cpp";case "JAVA"->"Main.java";default->throw new DemoRequestException("BOT_LANGUAGE_NOT_ALLOWED");}).getContentAsString(StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
 public WorkspaceStore.View create(Authentication auth,String id,String value){UUID user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){writable(t);String l=language(t,value);return store.create(UUID.fromString(t.id),user,l,template(l));}}
 public WorkspaceStore.View read(Authentication auth,String id,String value){UUID user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){if(!t.participantPolicy.bots())throw new AuthException("CODING_NOT_ALLOWED",403);return store.read(UUID.fromString(t.id),user,language(t,value));}}
 public WorkspaceStore.View save(Authentication auth,String id,String value,Long revision,String source){UUID user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){writable(t);if(!t.botPolicy.allowDraftSave())throw new AuthException("DRAFT_SAVE_DISABLED",403);String l=language(t,value);validateSource(source,t.botPolicy,l);if(revision==null)throw new DemoRequestException("WORKSPACE_REVISION_REQUIRED");return store.save(UUID.fromString(t.id),user,l,revision,source);}}
 public WorkspaceStore.View job(Authentication auth,String id,String value,Long revision,String source,String type){UUID user=accounts.requireUser(auth).id().value();var t=tournaments.find(id);synchronized(t){
  if(t.botPolicy.submissionMode()==BotSubmissionMode.PRE_UPLOADED_ONLY&&!type.equals("BUILD")){Instant now=Instant.now();if(t.status!=TournamentStatus.REGISTRATION||t.botPolicy.submissionDeadline()!=null&&!now.isBefore(t.botPolicy.submissionDeadline()))throw new AuthException("WORKSPACE_CLOSED",409);}else writable(t);String l=language(t,value);if(revision==null)throw new DemoRequestException("WORKSPACE_REVISION_REQUIRED");if(type.equals("TEST")&&!t.botPolicy.allowPublicTests()||type.equals("SAMPLE")&&!t.botPolicy.allowCustomTestMatch())throw new AuthException("WORKSPACE_TEST_DISABLED",403);
  if(type.equals("BUILD"))validateSource(source,t.botPolicy,l);
  store.claim(UUID.fromString(t.id),user,l,revision,source,t.botPolicy.maxSubmissionVersions(),type);
  return store.read(UUID.fromString(t.id),user,l);
 }}
 public List<Map<String,String>> publicCases(Authentication auth,String id){accounts.requireUser(auth);var t=tournaments.find(id);synchronized(t){if(!t.participantPolicy.bots())throw new AuthException("CODING_NOT_ALLOWED",403);if(!t.botPolicy.allowPublicTests())throw new AuthException("WORKSPACE_TEST_DISABLED",403);return tests.publicCases();}}
}
