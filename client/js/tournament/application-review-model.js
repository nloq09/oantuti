export function reviewDecisions(status,open){return open&&status!=='CANCELLED'?['APPROVED','WAITLISTED','REJECTED'].filter(next=>next!==status):[];}
export function reviewPayload(decision,reason,revision){
 if(!['APPROVED','REJECTED','WAITLISTED'].includes(decision))throw new Error('INVALID_REGISTRATION_TRANSITION');
 const text=(reason||'').trim();if(!text||text.length>500||/[\u0000-\u0008\u000b-\u001f\u007f]/.test(text))throw new Error('REASON_REQUIRED');
 return {decision,reason:text,revision};
}
