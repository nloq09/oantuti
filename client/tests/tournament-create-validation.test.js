import test from 'node:test';import assert from 'node:assert/strict';
import {validateCreateStep} from '../js/tournament/tournament-create-validation.js';
const data={name:'Cup',format:'ROUND_ROBIN',bestOf:3,maxParticipants:10,participantPolicy:'BOT_ONLY',maxConcurrentMatches:2,botPolicy:{submissionMode:'PRE_UPLOADED_ONLY',allowedLanguages:['PYTHON'],maxSubmissionVersions:10,maxSourceBytes:524288}};
test('wizard rejects blank names unsupported formats capacity and concurrency before create',()=>{
 for(const [step,patch]of [[0,{name:' '}],[0,{format:'FAKE'}],[0,{bestOf:7}],[1,{maxParticipants:1}],[1,{format:'TEAM_ROUND_ROBIN',maxParticipants:5}],[1,{maxParticipants:33}],[4,{maxConcurrentMatches:5}]])assert.throws(()=>validateCreateStep(step,{...data,...patch}));assert.equal(validateCreateStep(1,{...data,format:'SWISS',maxParticipants:4000}),true);
});
test('existing bot language source/version limits and group stages remain validated',()=>{
 assert.throws(()=>validateCreateStep(2,{...data,botPolicy:{...data.botPolicy,allowedLanguages:[]}}));assert.throws(()=>validateCreateStep(2,{...data,botPolicy:{...data.botPolicy,maxSourceBytes:524289}}));assert.throws(()=>validateCreateStep(2,{...data,bots:[{language:'CPP'}]}));assert.equal(validateCreateStep(2,{...data,participantPolicy:'HUMAN_ONLY',botPolicy:null}),true);assert.throws(()=>validateCreateStep(1,{...data,format:'GROUP_KNOCKOUT',stages:[{groups:16,qualifyTopN:2}]}));
});
test('manual timeline rejects invalid or reversed dates while optional dates remain optional',()=>{
 assert.equal(validateCreateStep(3,{...data,timing:{}}),true);assert.throws(()=>validateCreateStep(3,{...data,timing:{registrationDeadline:'2026-02-30T10:00'}}));assert.throws(()=>validateCreateStep(3,{...data,timing:{checkInDeadline:'2026-10-21T12:00',startTime:'2026-10-21T11:00'}}));
});
