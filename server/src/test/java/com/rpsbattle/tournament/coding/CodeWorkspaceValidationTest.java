package com.rpsbattle.tournament.coding;
import com.rpsbattle.codevalidation.BotTestService;
import com.rpsbattle.tournament.domain.TournamentBotPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CodeWorkspaceValidationTest {
 @Test void sourceValidationRejectsEmptyOversizeBinaryAndInvalidUnicode(){var p=TournamentBotPolicy.defaults();for(String invalid:new String[]{" ","x".repeat(524289),"x\0y","\uD800"})assertThatThrownBy(()->CodeWorkspaceService.validateSource(invalid,p,"PYTHON")).isInstanceOf(RuntimeException.class);assertThatCode(()->CodeWorkspaceService.validateSource("print('Chào')\n",p,"PYTHON")).doesNotThrowAnyException();}
 @Test void javaRequiresFixedMainEntrypointAndNoPackage(){var p=TournamentBotPolicy.defaults();assertThatThrownBy(()->CodeWorkspaceService.validateSource("package other; public class Main {}",p,"JAVA")).hasMessage("JAVA_MAIN_REQUIRED");assertThatThrownBy(()->CodeWorkspaceService.validateSource("class Other {}",p,"JAVA")).hasMessage("JAVA_MAIN_REQUIRED");assertThatCode(()->CodeWorkspaceService.validateSource("public class Main {}",p,"JAVA")).doesNotThrowAnyException();}
 @Test void publicFixturesExerciseBothPlayersAndChangingSequencesWithoutHiddenPayloads(){var service=new BotTestService(null);var cases=service.publicCases();assertThat(cases).hasSize(8);assertThat(cases.stream().map(c->c.get("input"))).anyMatch(s->s.contains("\"player\":\"P1\""));assertThat(cases.stream().map(c->c.get("input"))).anyMatch(s->s.contains("\"player\":\"P2\""));assertThat(cases.stream().map(c->c.get("input")).distinct()).hasSize(8);}
}
