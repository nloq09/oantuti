package com.rpsbattle.api.controller;
import com.rpsbattle.tournament.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/tournaments/{id}/teams")
public class TournamentTeamController {
 private final TeamTournamentService teams;private final TournamentService tournaments;
 public TournamentTeamController(TeamTournamentService teams,TournamentService tournaments){this.teams=teams;this.tournaments=tournaments;}
 @PostMapping public Map<String,Object> create(Authentication auth,@PathVariable String id,@RequestBody Create body){teams.create(auth,id,body.revision(),body.name(),body.school(),body.participantId());return tournaments.summary(tournaments.find(id));}
 @PostMapping("/{team}/members") public Map<String,Object> member(Authentication auth,@PathVariable String id,@PathVariable String team,@RequestBody Member body){teams.join(auth,id,body.revision(),team,body.participantId(),body.leave());return tournaments.summary(tournaments.find(id));}
 @PostMapping("/{team}/lineup") public Map<String,Object> lineup(Authentication auth,@PathVariable String id,@PathVariable String team,@RequestBody Lineup body){teams.lineup(auth,id,body.revision(),body.fixtureId(),team,body.participantIds());return tournaments.summary(tournaments.find(id));}
 public record Create(Long revision,String name,String school,String participantId){}
 public record Member(Long revision,String participantId,boolean leave){}
 public record Lineup(Long revision,String fixtureId,List<String> participantIds){}
}
