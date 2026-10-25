/** Server capabilities remain authoritative; monotonic time only counts down or closes controls. */
export class CodingSession {
 constructor(){this.flow=null;this.seen=new Set();}
 update(flow,received=performance.now()){
  if(this.flow?.codingCloseAt!==flow.codingCloseAt){this.seen.clear();this.previous=null;}
  this.flow=flow;this.received=received;this.duration=Date.parse(flow.codingCloseAt)-Date.parse(flow.serverNow);
 }
 remaining(now=performance.now()){return Math.max(0,Math.ceil((this.duration-(now-this.received))/1000));}
 writable(now=performance.now()){return this.flow?.canModifyWorkspace===true&&this.flow.phase==='CODING_OPEN'&&this.remaining(now)>0;}
 warning(now=performance.now()){
  const seconds=this.remaining(now),crossed=[1800,600,300,60].filter(limit=>seconds<=limit&&!this.seen.has(limit));
  for(const limit of crossed)this.seen.add(limit);return crossed.length?Math.min(...crossed):null;
 }
}
export function entryOptions(mode){return mode==='PRE_UPLOADED_ONLY'?['IMPORT']:mode==='CODE_IN_PLATFORM_ONLY'?['CODE']:['IMPORT','CODE'];}

/** A server snapshot can open a manual window; monotonic time can only close it. */
export class ManualSubmissionWindow {
 update(tournament,received=performance.now()){
  this.tournament=tournament;this.received=received;this.serverNow=Date.parse(tournament.serverNow);
 }
 writable(now=performance.now()){
  const t=this.tournament;if(!t||t.status!=='REGISTRATION'||t.submissionWindowOpen!==true||!Number.isFinite(this.serverNow))return false;
  const stamp=this.serverNow+Math.max(0,now-this.received),open=Date.parse(t.botPolicy?.submissionOpenAt),close=Date.parse(t.botPolicy?.submissionDeadline);
  return (!Number.isFinite(open)||stamp>=open)&&(!Number.isFinite(close)||stamp<close);
 }
}
