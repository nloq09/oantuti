package com.rpsbattle.admin;
import com.rpsbattle.account.domain.*;
import com.rpsbattle.account.repository.UserRepository;
import com.rpsbattle.auth.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AdminAuditPresentationTest {
    @Autowired AdminAuditService audit;@Autowired UserRepository users;@MockitoSpyBean JdbcTemplate db;
    User user(UserRole role){var now=Instant.now();var name="audit_"+UUID.randomUUID().toString().substring(0,8);return users.save(new User(UserId.random(),name,name+"@example.invalid","fixture-not-login",name,role,AccountStatus.ACTIVE,now,now,null,0));}
    UsernamePasswordAuthenticationToken auth(User user){return UsernamePasswordAuthenticationToken.authenticated(AccountPrincipal.from(user),null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role())));}
    UUID tournament(User owner,String name){UUID id=UUID.randomUUID();db.update("insert into tournaments(id,owner_user_id,name,format,status,created_at) values(?,?,?,'ROUND_ROBIN','DRAFT',?)",id,owner.id().value(),name,java.sql.Timestamp.from(Instant.now()));return id;}
    void event(UUID cup,int seq,String code,UUID actor,String target,String before,String after){db.update("insert into tournament_audit_events(id,tournament_id,sequence_number,event_type,actor_user_id,target_id,before_value,after_value,occurred_at) values(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),cup,seq,code,actor,target,before,after,java.sql.Timestamp.from(Instant.now()));}
    @SuppressWarnings("unchecked") List<Map<String,Object>> items(Map<String,Object> page){return (List<Map<String,Object>>)page.get("items");}
    @Test void joinsResolveNamesAndSearchFiltersBeforePagingWithTwoQueries(){
        var admin=user(UserRole.ADMIN);var player=user(UserRole.USER);var cup=tournament(admin,"Readable Audit Cup "+UUID.randomUUID());var registration=UUID.randomUUID();
        db.update("insert into tournament_timeline_registrations(id,tournament_id,user_id,display_name,intended_language,status,registered_at,rules_accepted_at) values(?,?,?,?,'PYTHON','APPROVED',?,?)",registration,cup,player.id().value(),player.username(),java.sql.Timestamp.from(Instant.now()),java.sql.Timestamp.from(Instant.now()));
        for(int n=1;n<=31;n++)event(cup,n,"PARTICIPANT_APPLICATION_REVIEWED",admin.id().value(),registration.toString(),"PENDING","APPROVED");
        clearInvocations(db);
        var page=audit.list(auth(admin),Map.of("q",player.username(),"category","REGISTRATION","actor_kind","HUMAN"),1,25);
        assertThat(page.get("total")).isEqualTo(31L);assertThat(items(page)).hasSize(6);assertThat(items(page).getFirst()).containsEntry("resource_name",player.username()).containsEntry("category","REGISTRATION");
        long reads=mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().equals("query")&&i.getMethod().getParameterCount()==3&&i.getMethod().getParameterTypes()[1]==org.springframework.jdbc.core.PreparedStatementSetter.class&&i.getArguments()[0].toString().contains(") directory")).count();assertThat(reads).isEqualTo(2);
    }
    @Test void systemAndValidationContextAndRedactionPreserveOriginalRecord(){
        var admin=user(UserRole.ADMIN);var cup=tournament(admin,"Audit safety "+UUID.randomUUID());String raw="{\"status\":\"PENDING\",\"password\":\"fixture-private-pass\",\"nested\":{\"workerCredential\":\"fixture-private-worker\"}}";
        event(cup,1,"TIMELINE_BOT_VALIDATION_PASSED",null,admin.id().value().toString(),raw,"{\"status\":\"APPROVED\"}");
        var row=items(audit.list(auth(admin),Map.of("q","Audit safety","category","VALIDATION","actor_kind","SYSTEM"),0,25)).stream().filter(r->r.get("resource_id").equals(admin.id().value().toString())).findFirst().orElseThrow();
        assertThat(row).containsEntry("role_source","SYSTEM").containsEntry("resource_type","SUBMISSION").containsEntry("resource_name",admin.username());assertThat(row.toString()).doesNotContain("fixture-private-");
        assertThat(db.queryForObject("select before_value from tournament_audit_events where tournament_id=?",String.class,cup)).isEqualTo(raw);
    }
    @Test void resultAndAdvancedFiltersAndInvalidFiltersAreEnforced(){
        var admin=user(UserRole.ADMIN);UUID id=UUID.randomUUID();db.update("insert into admin_operation_events(id,actor_user_id,actor_role,resource_type,resource_id,action,result,http_status,occurred_at) values(?,?,'ADMIN','SYSTEM',?,'POST_REQUEST','FAILURE',409,?)",id,admin.id().value(),id.toString(),java.sql.Timestamp.from(Instant.now()));
        assertThat(items(audit.list(auth(admin),Map.of("resource_id",id.toString(),"result","CONFLICT","http_status","409","category","SECURITY"),0,25))).hasSize(1);
        assertThat(items(audit.list(auth(admin),Map.of("q","%_' or 1=1"),0,25))).isEmpty();
        for(var filters:List.of(Map.of("category","INVALID"),Map.of("http_status","999"),Map.of("actor_kind","INVALID"),Map.of("from","bad-date")))assertThatThrownBy(()->audit.list(auth(admin),filters,0,25)).isInstanceOf(com.rpsbattle.auth.AuthException.class);
        assertThatThrownBy(()->audit.list(auth(user(UserRole.USER)),Map.of(),0,25)).isInstanceOf(com.rpsbattle.auth.AuthException.class);
    }
    @Test void workerEventsAppearWithoutCredentialJoins(){var admin=user(UserRole.ADMIN);var id=UUID.randomUUID();db.update("insert into worker_identity_events(id,event_type,resource_id,actor_user_id,occurred_at) values(?,'REGISTRATION_TOKEN_CREATED',?,?,?)",UUID.randomUUID(),id,admin.id().value(),java.sql.Timestamp.from(Instant.now()));assertThat(items(audit.list(auth(admin),Map.of("resource_id",id.toString(),"category","WORKER","resource_type","WORKER_TOKEN"),0,25))).hasSize(1);}
}
