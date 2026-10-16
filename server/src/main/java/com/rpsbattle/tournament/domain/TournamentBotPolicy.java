package com.rpsbattle.tournament.domain;
import com.rpsbattle.bot.upload.BotLanguage;
import com.rpsbattle.online.DemoRequestException;
import java.time.Instant;
import java.util.Set;

/** Tournament-owned configuration, never a participant-selected runtime/image. */
public record TournamentBotPolicy(BotSubmissionMode submissionMode,Set<String> allowedLanguages,
 Instant submissionOpenAt,Instant submissionDeadline,Integer maxSubmissionVersions,
 Boolean allowDraftSave,Boolean allowPublicTests,Boolean allowCustomTestMatch,Boolean hiddenTestsEnabled,
 Boolean lockOnSubmit,Boolean lockAtDeadline,Boolean allowResubmitBeforeDeadline,Integer maxSourceBytes,
 Double cpuLimit,Integer memoryLimit,Integer turnTimeoutMs,Integer buildTimeoutMs,Integer testTimeoutMs,
 DeadlineBehavior deadlineBehavior,Boolean organizerSourceAccess) {
 public enum DeadlineBehavior { NO_SUBMISSION, AUTO_SUBMIT_LATEST_READY }
 public TournamentBotPolicy {
  submissionMode=submissionMode==null?BotSubmissionMode.PRE_UPLOADED_ONLY:submissionMode;
  if(allowedLanguages!=null&&allowedLanguages.stream().anyMatch(java.util.Objects::isNull))throw new DemoRequestException("INVALID_BOT_SUBMISSION_POLICY");
  allowedLanguages=allowedLanguages==null?Set.of("PYTHON","CPP","JAVA"):Set.copyOf(allowedLanguages);
  maxSubmissionVersions=maxSubmissionVersions==null?10:maxSubmissionVersions;
  allowDraftSave=allowDraftSave==null||allowDraftSave;allowPublicTests=allowPublicTests==null||allowPublicTests;
  allowCustomTestMatch=allowCustomTestMatch==null||allowCustomTestMatch;hiddenTestsEnabled=Boolean.TRUE.equals(hiddenTestsEnabled);
  lockOnSubmit=Boolean.TRUE.equals(lockOnSubmit);lockAtDeadline=lockAtDeadline==null||lockAtDeadline;
  allowResubmitBeforeDeadline=allowResubmitBeforeDeadline==null||allowResubmitBeforeDeadline;
  maxSourceBytes=maxSourceBytes==null?524288:maxSourceBytes;cpuLimit=cpuLimit==null?0.5:cpuLimit;
  memoryLimit=memoryLimit==null?384:memoryLimit;turnTimeoutMs=turnTimeoutMs==null?2000:turnTimeoutMs;
  buildTimeoutMs=buildTimeoutMs==null?25000:buildTimeoutMs;testTimeoutMs=testTimeoutMs==null?60000:testTimeoutMs;
  deadlineBehavior=deadlineBehavior==null?DeadlineBehavior.NO_SUBMISSION:deadlineBehavior;
  organizerSourceAccess=Boolean.TRUE.equals(organizerSourceAccess);
  if(allowedLanguages.isEmpty()||!Set.of("PYTHON","CPP","JAVA").containsAll(allowedLanguages)
   ||maxSubmissionVersions<1||maxSubmissionVersions>100||maxSourceBytes<1||maxSourceBytes>524288
   ||submissionOpenAt!=null&&submissionDeadline!=null&&!submissionOpenAt.isBefore(submissionDeadline)
   ||submissionDeadline==null&&(deadlineBehavior==DeadlineBehavior.AUTO_SUBMIT_LATEST_READY)
   ||cpuLimit!=0.5||memoryLimit!=384||turnTimeoutMs!=2000||buildTimeoutMs!=25000||testTimeoutMs!=60000
   ||organizerSourceAccess)throw new DemoRequestException("INVALID_BOT_SUBMISSION_POLICY");
 }
 public static TournamentBotPolicy defaults(){return new TournamentBotPolicy(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);}
 public void requirePreuploaded(BotLanguage language){
  if(submissionMode==BotSubmissionMode.CODE_IN_PLATFORM_ONLY)throw new DemoRequestException("PLATFORM_CODE_REQUIRED");
  if(!allowedLanguages.contains(language==BotLanguage.JAR?"JAVA":language.name()))throw new DemoRequestException("BOT_LANGUAGE_NOT_ALLOWED");
 }
}
