package com.rpsbattle.tournament.persistence;
import com.rpsbattle.tournament.domain.TournamentBotPolicy;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
@Converter public class TournamentBotPolicyConverter implements AttributeConverter<TournamentBotPolicy,String> {
 private static final com.fasterxml.jackson.databind.ObjectMapper JSON=JsonMapper.builder().addModule(new JavaTimeModule()).build();
 public String convertToDatabaseColumn(TournamentBotPolicy policy){try{return JSON.writeValueAsString(policy);}catch(Exception e){throw new IllegalArgumentException("Cannot encode bot policy",e);}}
 public TournamentBotPolicy convertToEntityAttribute(String json){try{return JSON.readValue(json,TournamentBotPolicy.class);}catch(Exception e){throw new IllegalArgumentException("Cannot decode bot policy",e);}}
}
