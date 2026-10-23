package com.rpsbattle.tournament;
import com.rpsbattle.tournament.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.net.*;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
class PublicTournamentReadTest extends StageThreeWebFixture {
 private void publicOnly(JsonNode data){
  assertThat(data.toString()).doesNotContain("\"audit\"","\"seedingSeed\"","\"userId\"","\"botVersionId\"","\"sourceHash\"","\"artifactHash\"","private-review-note","private-audit-note","hidden-bot-token");
 }
 private JsonNode anonymous(HttpClient http,String path)throws Exception{
  var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());
 }
 @Test void publicHttpAndSocketReadsExcludeManagementDataWhileOwnerRetainsAccess()throws Exception{
  loginAccount();var created=request("/api/tournaments","POST",Map.of("name","Private data boundary","format","SINGLE_ELIMINATION","bestOf",3),201);
  var id=created.path("id").asText();var t=tournaments.find(id);var user=account.id().value();var rid=UUID.randomUUID();var now=java.time.Instant.now();var firstGame=UUID.randomUUID().toString();
  var botA=upload("Public read A");var botB=upload("Public read B");
  var a=new TournamentParticipant(rid.toString(),botA.id(),"hidden-bot-token","A",1,user).withHash("a".repeat(64));var b=new TournamentParticipant(UUID.randomUUID().toString(),botB.id(),"","B",2,user);
  synchronized(t){
   t.participants.addAll(List.of(a,b));t.registrations.add(new TournamentRegistration(rid,UUID.fromString(id),user,UUID.fromString(a.botId()),UUID.fromString(a.botId()),"A",TournamentRegistration.Status.APPROVED,now,now,null).withSourceHash("b".repeat(64)));
   for(int i=1;i<=2;i++){var m=new TournamentMatch(i==1?firstGame:UUID.randomUUID().toString(),id,a,b);m.seriesId=firstGame;m.gameNumber=i;m.status=TournamentMatch.Status.FINISHED;m.winnerParticipantId=a.participantId();t.matches.add(m);}
   t.bracket.add(new BracketNode("final",1,1,firstGame,a.participantId(),b.participantId(),null,null,null,a.participantId(),"WIN"));
   t.teams=new TeamState(List.of(),List.of(new TeamState.Fixture("fixture",1,"a","b",List.of(),List.of(),true,List.of(firstGame))));
   t.operations=t.operations.review(firstGame,"private-review-note");TournamentAudit.append(t,"MATCH_REVIEW_REQUESTED",account.id().value(),firstGame,"private-audit-note",null,null);
   tournaments.changed(t);
  }
  String path="/api/tournaments/"+id;
  try(var http=HttpClient.newHttpClient();var peer=new Peer()){
   for(String suffix:List.of("","/export","/summary","/bracket","/matches","/fixtures/fixture/matches"))publicOnly(anonymous(http,path+suffix));
   publicOnly(anonymous(http,"/api/tournaments?owner="+account.id().value()));
   var bracket=anonymous(http,path+"/bracket");assertThat(bracket.path("series").get(0).path("reviewRequired").asBoolean()).isTrue();assertThat(bracket.path("matches").get(0).path("reviewRequired").asBoolean()).isFalse();
   var manager=request(path,"GET",null,200);assertThat(manager.has("audit")).isTrue();assertThat(manager.path("reviews").path(firstGame).asText()).isEqualTo("private-review-note");
   peer.send(Map.of("type","WATCH_TOURNAMENT","tournamentId",id));publicOnly(peer.await(m->m.path("type").asText().equals("TOURNAMENT_STATE")));
   synchronized(t){TournamentAudit.append(t,"MATCH_REVIEW_REQUESTED",account.id().value(),firstGame,"private-audit-note",null,null);tournaments.changed(t);}
   var event=peer.await(m->m.path("payload").has("event"));publicOnly(event);assertThat(event.path("payload").path("event").has("reason")).isFalse();assertThat(event.path("payload").path("event").has("actor")).isFalse();
  }
  String name="other_"+UUID.randomUUID().toString().substring(0,8);registration.register(name,name+"@example.com","Tournament Test Password","Other");
  request("/api/auth/logout","POST",Map.of(),200);request("/api/auth/login","POST",Map.of("username",name,"password","Tournament Test Password"),200);
  publicOnly(request(path,"GET",null,200));publicOnly(request(path+"/export","GET",null,200));
 }
}
