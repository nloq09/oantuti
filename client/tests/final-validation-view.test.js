import {validationStageCodes} from '../js/tournament/validation-detail-state.js';
const serverSteps=(state='PENDING',patch={})=>({...Object.fromEntries(validationStageCodes.map(code=>[code,state])),...patch});
import test from 'node:test';
import assert from 'node:assert/strict';
import {validationNextStep,renderFinalValidation} from '../js/tournament/final-validation-view.js';
import {setup,descendants} from './helpers/tournament-dom.js';
test('final PASS retains historical result without promising check-in after cancellation or completion',()=>{
 for(const status of ['CANCELLED','FINISHED','RUNNING','PAUSED','RECOVERY_REQUIRED']){
  const root=setup();renderFinalValidation(root,{id:'cup',status},{phase:status,finalValidation:{status:'PASSED',applied:true,steps:serverSteps('PASS')}});
  const text=descendants(root).map(n=>n.textContent).join(' ');
  assert.match(text,/KIỂM THỬ HOÀN TẤT/);assert.doesNotMatch(text,/Chờ giai đoạn check-in/);
 }
 assert.match(validationNextStep({status:'CANCELLED'},{phase:'CHECK_IN_OPEN',checkIn:{canCheckIn:true}}),/Giải đã hủy/);
});
test('successful validation guidance follows check-in and participation state',()=>{
 const t={status:'REGISTRATION'};
 assert.match(validationNextStep(t,{phase:'VALIDATING'}),/Chờ giai đoạn check-in/);
 assert.match(validationNextStep(t,{phase:'CHECK_IN_OPEN',checkIn:{canCheckIn:true}}),/Hãy check-in/);
 assert.match(validationNextStep(t,{checkIn:{status:'CHECKED_IN'}}),/Đã check-in/);
 assert.match(validationNextStep(t,{phase:'READY_TO_START',checkIn:{status:'MISSED'}}),/lỡ hạn/);
 assert.match(validationNextStep({status:'RUNNING'},{participantStatus:'ELIMINATED'}),/bị loại/);
 assert.doesNotMatch(validationNextStep(t,{phase:'READY_TO_START'}),/Chờ giai đoạn check-in/);
});
