package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import com.rpsbattle.tournament.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TournamentTimelineTest {
    static final Instant START=Instant.parse("2026-10-19T00:00:00Z");
    static TournamentTimeline timeline(){return from(new long[]{0,60,120,180,190,210,240,270,300,360,400});}
    static TournamentTimeline from(long[] seconds){return new TournamentTimeline(time(seconds[0]),time(seconds[1]),time(seconds[2]),time(seconds[3]),time(seconds[4]),time(seconds[5]),time(seconds[6]),time(seconds[7]),time(seconds[8]),time(seconds[9]),time(seconds[10]));}
    static Instant time(long seconds){return START.plusSeconds(seconds);}
    @Test void validatesScheduleAndAllowsAdjacentWindows(){
        assertThatCode(()->from(new long[]{0,60,60,120,120,180,180,240,240,300,300})).doesNotThrowAnyException();
        var t=timeline();assertThat(t.codingOpenAt()).isEqualTo(time(120));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9})
    void rejectsEveryReversedBoundary(int index){long[] values={0,60,120,180,190,210,240,270,300,360,400};values[index]=values[index+1]+1;assertThatThrownBy(()->from(values)).hasMessage("INVALID_TOURNAMENT_TIMELINE");}
    @Test void requiresAllMandatoryDatesButResultPublicationIsOptional(){
        var t=timeline();assertThatThrownBy(()->new TournamentTimeline(null,t.registrationCloseAt(),t.codingOpenAt(),t.codingCloseAt(),t.validationStartAt(),t.validationDeadline(),t.checkInOpenAt(),t.checkInCloseAt(),t.tournamentStartAt(),t.estimatedEndAt(),null)).hasMessage("INVALID_TOURNAMENT_TIMELINE");
        assertThatCode(()->new TournamentTimeline(t.registrationOpenAt(),t.registrationCloseAt(),t.codingOpenAt(),t.codingCloseAt(),t.validationStartAt(),t.validationDeadline(),t.checkInOpenAt(),t.checkInCloseAt(),t.tournamentStartAt(),t.estimatedEndAt(),null)).doesNotThrowAnyException();
    }
    @Test void survivesDatabaseJsonSerializationAndOldSnapshotsWithoutTimeline(){
        var tournament=new Tournament(UUID.randomUUID().toString(),"Timeline persistence",TournamentFormat.ROUND_ROBIN,START);tournament.timeline=timeline();
        var converter=new TournamentDetailsConverter();String stored=converter.convertToDatabaseColumn(TournamentDetails.snapshot(tournament));
        var restored=new Tournament(tournament.id,tournament.name,tournament.format,START);converter.convertToEntityAttribute(stored).restore(restored);assertThat(restored.timeline).isEqualTo(tournament.timeline);
        var tree=new com.fasterxml.jackson.databind.ObjectMapper();try{var old=(com.fasterxml.jackson.databind.node.ObjectNode)tree.readTree(stored);old.remove("timeline");converter.convertToEntityAttribute(old.toString()).restore(restored);assertThat(restored.timeline).isNull();}catch(Exception failure){throw new AssertionError(failure);}
    }
    @Test void explicitTimezoneOffsetsRepresentTheSameServerInstant()throws Exception{
        var mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder().addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        String json=mapper.writeValueAsString(timeline()).replace("2026-10-19T00:00:00Z","2026-10-19T07:00:00+07:00");
        assertThat(mapper.readValue(json,TournamentTimeline.class)).isEqualTo(timeline());
        assertThatThrownBy(()->mapper.readValue(json.replace("2026-10-19T07:00:00+07:00","2026-10-19T07:00:00"),TournamentTimeline.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
}
