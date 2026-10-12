package com.rpsbattle.tournament;
import com.rpsbattle.tournament.registry.*;
import com.rpsbattle.tournament.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TournamentRegistryTest {
    @Test void uniqueCreationLookupAndRemoval(){var registry=new TournamentRegistry();var a=registry.create(" A ",TournamentFormat.ROUND_ROBIN);var b=registry.create("B",TournamentFormat.ROUND_ROBIN);assertThat(a.id).isNotEqualTo(b.id);assertThat(a.name).isEqualTo("A");assertThat(a.status).isEqualTo(TournamentStatus.DRAFT);assertThat(registry.findAll()).hasSize(2);registry.remove(a.id);assertThat(registry.findById(a.id)).isEmpty();assertThat(registry.findById(b.id)).contains(b);}
    @Test void rejectsEmptyAndOversizedNames(){var registry=new TournamentRegistry();assertThatThrownBy(()->registry.create(" ",TournamentFormat.ROUND_ROBIN)).hasMessage("INVALID_TOURNAMENT_NAME");assertThatThrownBy(()->registry.create("a".repeat(81),TournamentFormat.ROUND_ROBIN)).hasMessage("INVALID_TOURNAMENT_NAME");}
}
