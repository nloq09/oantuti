export const submissionLocked=s=>s?.status==='LOCKED';
export function canSubmitVersion(version,submission,accepted){return Boolean(accepted&&version?.status==='READY'&&!submissionLocked(submission)&&submission?.status!=='DISQUALIFIED');}
export function hiddenSummary(value){try{const parsed=JSON.parse(value);return {passed:parsed.passed===true,cases:(parsed.cases||[]).map(c=>({id:c.id,status:c.status}))};}catch{return null;}}
export const submissionActions=(status,phase)=>!['REGISTRATION','CHECK_IN'].includes(phase)?[]:status==='SUBMITTED'?['lock','retest','reject']:status==='LOCKED'?['retest','reject']:status==='REJECTED'?['allow-resubmit','retest']:[];
