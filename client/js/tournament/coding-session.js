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
