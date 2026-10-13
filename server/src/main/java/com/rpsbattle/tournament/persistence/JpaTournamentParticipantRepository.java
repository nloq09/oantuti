package com.rpsbattle.tournament.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface JpaTournamentParticipantRepository extends JpaRepository<TournamentParticipantEntity,UUID> {List<TournamentParticipantEntity> findByTournamentIdOrderById(UUID id);}
