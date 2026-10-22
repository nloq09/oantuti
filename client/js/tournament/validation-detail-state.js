// FinalValidationWorker emits PASS/FAIL for steps and PASSED/FAILED for overall status.
export const validationStageCodes=['BUILD','PROTOCOL','TIMEOUT','RESOURCE','LEGAL_ACTION','HIDDEN_TESTS','TOURNAMENT_TESTS','ARTIFACT_HASH'];
export const normalizeValidationStep=value=>({PASS:'PASSED',FAIL:'FAILED'})[value]||value;
const known=new Set(['PASSED','FAILED','RUNNING','PENDING','QUEUED','INFRA_ERROR','SKIPPED']);
export function validationDetailState(validation,requestState){
 if(requestState==='LOADING')return {status:'LOADING'};
 if(requestState==='ERROR')return {status:'ERROR',code:'VALIDATION_REFRESH_FAILED'};
 if(!validation)return {status:'IDLE'};
 if(validation.steps==null)return {status:'LOADING'};
 if(typeof validation.steps!=='object'||Array.isArray(validation.steps))return {status:'ERROR',code:'VALIDATION_STATE_INCONSISTENT'};
 const steps=Object.fromEntries(validationStageCodes.map(code=>[code,normalizeValidationStep(validation.steps[code])]));
 const complete=Object.values(steps).every(value=>known.has(value));
 if(!complete||validation.status==='PASSED'&&Object.values(steps).some(value=>value!=='PASSED'))return {status:'ERROR',code:'VALIDATION_STATE_INCONSISTENT'};
 const passed=Object.values(steps).filter(value=>value==='PASSED').length;
 return {status:'READY',steps,passed,total:validationStageCodes.length};
}
