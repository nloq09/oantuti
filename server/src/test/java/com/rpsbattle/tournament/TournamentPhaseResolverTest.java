package com.rpsbattle.tournament;

import com.rpsbattle.tournament.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.rpsbattle.tournament.TournamentTimelineTest.*;

class TournamentPhaseResolverTest {
    TournamentPhaseResolver.Resolution at(long second){return TournamentPhaseResolver.resolve(TournamentStatus.REGISTRATION,timeline(),time(second),null,true);}
    @Test void exactBoundariesUseHalfOpenWindowsWithoutCron(){
        long[] seconds={-1,0,59,60,119,120,179,180,189,190,210,239,240,269,270,300,1000};
        String[] expected={"UPCOMING","REGISTRATION_OPEN","REGISTRATION_OPEN","WAITING_FOR_CODING","WAITING_FOR_CODING","CODING_OPEN","CODING_OPEN","SUBMISSION_FROZEN","SUBMISSION_FROZEN","VALIDATING","VALIDATING","VALIDATING","CHECK_IN_OPEN","CHECK_IN_OPEN","READY_TO_START","READY_TO_START","READY_TO_START"};
        for(int i=0;i<seconds.length;i++)assertThat(at(seconds[i]).phase().name()).as("server second %s",seconds[i]).isEqualTo(expected[i]);
        assertThat(at(120).phaseEndsAt()).isEqualTo(time(180));
    }
    @Test void pendingReviewAndWaitingAreDistinct(){assertThat(TournamentPhaseResolver.resolve(TournamentStatus.REGISTRATION,timeline(),time(60),null,false).phase()).isEqualTo(TournamentPhase.REGISTRATION_REVIEW);}
    @Test void actualLifecycleTakesPrecedenceOverClockAndOverrides(){
        for(var status: new TournamentStatus[]{TournamentStatus.DRAFT,TournamentStatus.PAUSED,TournamentStatus.RECOVERY_REQUIRED,TournamentStatus.CANCELLED,TournamentStatus.RUNNING,TournamentStatus.FINISHED})assertThat(TournamentPhaseResolver.resolve(status,timeline(),time(-1),TournamentPhase.CODING_OPEN,true).phase().name()).isEqualTo(status.name());
    }
    @Test void legacyManualFlowIsPreserved(){
        assertThat(TournamentPhaseResolver.resolve(TournamentStatus.REGISTRATION,null,time(0),null,true).phase()).isEqualTo(TournamentPhase.REGISTRATION_OPEN);
        assertThat(TournamentPhaseResolver.resolve(TournamentStatus.CHECK_IN,null,time(0),null,true).phase()).isEqualTo(TournamentPhase.CHECK_IN_OPEN);
        assertThat(TournamentPhaseResolver.resolve(TournamentStatus.READY,null,time(0),null,true).phase()).isEqualTo(TournamentPhase.READY_TO_START);
    }
    @Test void explicitOverrideCannotFabricateEngineState(){
        assertThat(TournamentPhaseResolver.resolve(TournamentStatus.REGISTRATION,timeline(),time(-1),TournamentPhase.CODING_OPEN,true).phase()).isEqualTo(TournamentPhase.CODING_OPEN);
        assertThatThrownBy(()->TournamentPhaseResolver.resolve(TournamentStatus.READY,timeline(),time(1000),TournamentPhase.RUNNING,true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void extendedTimelineResolvesAgainAfterFreezeAndAcrossRestart(){
        assertThat(at(185).phase()).isEqualTo(TournamentPhase.SUBMISSION_FROZEN);
        var extended=from(new long[]{0,60,120,200,210,220,240,270,300,360,400});
        assertThat(TournamentPhaseResolver.resolve(TournamentStatus.REGISTRATION,extended,time(185),null,true).phase()).isEqualTo(TournamentPhase.CODING_OPEN);
        assertThat(at(250)).isEqualTo(TournamentPhaseResolver.resolve(TournamentStatus.REGISTRATION,timeline(),time(250),null,true));
    }
}
