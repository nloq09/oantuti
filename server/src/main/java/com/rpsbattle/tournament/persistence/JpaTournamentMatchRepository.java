package com.rpsbattle.tournament.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface JpaTournamentMatchRepository extends JpaRepository<TournamentMatchEntity,UUID> {List<TournamentMatchEntity> findByTournamentIdOrderById(UUID id);}
