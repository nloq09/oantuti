package com.rpsbattle.history;
import com.rpsbattle.history.domain.MatchMetadata;
import com.rpsbattle.online.OnlineMatch;
import java.time.Duration;
import java.util.*;

public final class MatchMetadataMapper {
    private MatchMetadataMapper() {}
    public static MatchMetadata capture(OnlineMatch room) {
        synchronized(room) {
            var state=room.state();var result=state.result();
            var winner=result.flatMap(r->r.winner()).map(p->p.value()).orElse(null);
            var roster=room.seats().stream().sorted(Comparator.comparing(s->s.id().value())).map(seat->{
                var identity=room.seatIdentity(seat.id());
                var type=room.uploadedBot(seat.id()) ? MatchMetadata.ControllerType.UPLOADED_BOT : seat.bot()? MatchMetadata.ControllerType.BUILTIN_BOT : MatchMetadata.ControllerType.HUMAN;
                return new MatchMetadata.Participant(seat.id().value(),identity.userId(),identity.botId(),type);
            }).toList();
            var winnerUser=winner==null?null:roster.stream().filter(p->p.seat().equals(winner)).map(MatchMetadata.Participant::userId).filter(Objects::nonNull).findFirst().orElse(null);
            Long duration=room.startedAt()==null || room.finishedAt()==null?null:Math.max(0,Duration.between(room.startedAt(),room.finishedAt()).toMillis());
            return new MatchMetadata(room.archiveId(),room.id(),room.mode().name(),room.infrastructureError()==null?room.status():"INFRASTRUCTURE_ERROR",
                room.seatIdentity(OnlineMatch.P1).userId(),room.seatIdentity(OnlineMatch.P2).userId(),winnerUser,winner,
                result.map(r->r.outcome().name()).orElse(null),state.turnNumber(),room.startedAt(),room.finishedAt(),duration,room.ruleSetName(),room.createdAt(),roster);
        }
    }
}
