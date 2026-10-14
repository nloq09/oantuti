package com.rpsbattle.tournament.persistence;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
@Converter
public class TournamentDetailsConverter implements AttributeConverter<TournamentDetails,String> {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=JsonMapper.builder().addModule(new JavaTimeModule()).build();
    public static boolean equivalent(TournamentDetails a,TournamentDetails b){try{return JSON.readTree(JSON.writeValueAsString(a)).equals(JSON.readTree(JSON.writeValueAsString(b)));}catch(Exception e){throw new IllegalArgumentException(e);}}
    public String convertToDatabaseColumn(TournamentDetails value) {
        if(value==null)return null;
        try{return JSON.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Cannot encode tournament structure",e);}
    }
    public TournamentDetails convertToEntityAttribute(String value) {
        if(value==null)return null;
        try{return JSON.readValue(value,TournamentDetails.class);}catch(Exception e){throw new IllegalArgumentException("Cannot decode tournament structure",e);}
    }
}
