package com.rpsbattle.history.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
interface JpaParticipantRepository extends JpaRepository<ParticipantEntity,ParticipantEntity.Key> {
    List<ParticipantEntity> findByIdMatchIdOrderByIdSeat(String id);
    List<ParticipantEntity> findByIdMatchIdIn(Collection<String> ids);
}
