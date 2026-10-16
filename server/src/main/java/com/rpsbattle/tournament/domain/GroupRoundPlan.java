package com.rpsbattle.tournament.domain;
import com.rpsbattle.online.DemoRequestException;
import java.util.List;
/** Each configured stage is a seeded round-robin group phase, followed by one final knockout. */
public record GroupRoundPlan(int groups,int qualifyTopN) {
 public static List<GroupRoundPlan> defaults(){return List.of(new GroupRoundPlan(2,2));}
 public static void validate(List<GroupRoundPlan> plans,int entrants){
  if(plans==null||plans.isEmpty()||plans.size()>8)throw new DemoRequestException("INVALID_STAGE_PLAN");
  for(var plan:plans){if(plan==null||plan.groups<1||plan.groups>16||plan.qualifyTopN<1||plan.qualifyTopN>16||entrants/plan.groups<plan.qualifyTopN||entrants/plan.groups<2||plan.groups*plan.qualifyTopN<2||plan.groups*plan.qualifyTopN>entrants)throw new DemoRequestException("INVALID_STAGE_PLAN");entrants=plan.groups*plan.qualifyTopN;}
 }
}
