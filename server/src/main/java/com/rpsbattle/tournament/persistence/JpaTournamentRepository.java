package com.rpsbattle.tournament.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface JpaTournamentRepository extends JpaRepository<TournamentEntity,UUID> {List<TournamentEntity> findByVisibilityAndTrashedAtIsNullOrderByCreatedAtAsc(String visibility);}
