package com.rpsbattle.tournament.persistence;
import com.rpsbattle.tournament.domain.TournamentBotPolicy;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="tournament_bot_policies")
public class TournamentBotPolicyEntity {
 @Id @Column(name="tournament_id") UUID tournamentId;
 @Column(name="submission_mode",nullable=false,length=32) String submissionMode;
 @Column(name="allowed_languages",nullable=false,length=32) String allowedLanguages;
 @Convert(converter=TournamentBotPolicyConverter.class) @Column(name="policy_json",nullable=false,columnDefinition="text") TournamentBotPolicy policy;
 protected TournamentBotPolicyEntity(){}
 TournamentBotPolicyEntity(UUID id,TournamentBotPolicy policy){this.tournamentId=id;setPolicy(policy);}
 void setPolicy(TournamentBotPolicy value){policy=value;submissionMode=value.submissionMode().name();allowedLanguages=String.join(",",new java.util.TreeSet<>(value.allowedLanguages()));}
 TournamentBotPolicy domain(){if(!policy.submissionMode().name().equals(submissionMode)||!String.join(",",new java.util.TreeSet<>(policy.allowedLanguages())).equals(allowedLanguages))throw new IllegalStateException("Inconsistent tournament bot policy");return policy;}
}
