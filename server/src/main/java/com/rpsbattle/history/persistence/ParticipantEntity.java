package com.rpsbattle.history.persistence;
import com.rpsbattle.history.domain.MatchMetadata;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;

@Entity @Table(name="match_participants")
public class ParticipantEntity {
    @EmbeddedId Key id;
    @Column(name="seat_user_id") UUID userId;
    @Column(name="seat_bot_id") UUID botId;
    @Enumerated(EnumType.STRING) @Column(name="controller_type",nullable=false,length=24) MatchMetadata.ControllerType controllerType;
    protected ParticipantEntity() {}
    ParticipantEntity(String matchId,MatchMetadata.Participant p) {id=new Key(matchId,p.seat());copy(p);}
    void copy(MatchMetadata.Participant p) {userId=p.userId();botId=p.botId();controllerType=p.controllerType();}
    MatchMetadata.Participant domain() {return new MatchMetadata.Participant(id.seat,userId,botId,controllerType);}
    @Embeddable public static class Key implements Serializable {
        @Column(name="match_id",length=64) String matchId;
        @Column(length=2) String seat;
        public Key() {}
        Key(String matchId,String seat) {this.matchId=matchId;this.seat=seat;}
        public boolean equals(Object o) {return o instanceof Key k && Objects.equals(matchId,k.matchId) && Objects.equals(seat,k.seat);}
        public int hashCode() {return Objects.hash(matchId,seat);}
    }
}
