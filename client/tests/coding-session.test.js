import test from 'node:test';
import assert from 'node:assert/strict';
import {CodingSession,ManualSubmissionWindow,entryOptions} from '../js/tournament/coding-session.js';
function flow(seconds,extra={}){return {phase:'CODING_OPEN',canModifyWorkspace:true,serverNow:'2026-10-19T00:00:00Z',codingCloseAt:new Date(Date.parse('2026-10-19T00:00:00Z')+seconds*1000).toISOString(),...extra};}
test('countdown uses server time and monotonic elapsed time; expiry never opens a future phase',()=>{
 const s=new CodingSession();s.update(flow(10),500);assert.equal(s.remaining(5500),5);assert.equal(s.writable(10499),true);assert.equal(s.writable(10500),false);assert.equal(s.remaining(99999),0);s.update(flow(10,{phase:'WAITING_FOR_CODING',canModifyWorkspace:false}),0);assert.equal(s.writable(1000),false);
});
test('server can revoke editing while time remains and local clock cannot grant it back',()=>{
 const s=new CodingSession();s.update(flow(100,{canModifyWorkspace:false}),0);assert.equal(s.remaining(1000),99);assert.equal(s.writable(1000),false);s.update(flow(100,{phase:'PAUSED'}),0);assert.equal(s.writable(1000),false);
});
test('thirty, ten, five and one minute warnings occur once and missed intervals use the nearest warning',()=>{
 const s=new CodingSession();s.update(flow(1900),0);assert.equal(s.warning(100000),1800);assert.equal(s.warning(100001),null);assert.equal(s.warning(1300000),600);assert.equal(s.warning(1600000),300);assert.equal(s.warning(1840000),60);assert.equal(s.warning(1840001),null);
 const late=new CodingSession();late.update(flow(40),0);assert.equal(late.warning(0),60);assert.equal(late.warning(1),null);
});
test('submission modes expose only valid entry choices',()=>{
 assert.deepEqual(entryOptions('PRE_UPLOADED_ONLY'),['IMPORT']);assert.deepEqual(entryOptions('CODE_IN_PLATFORM_ONLY'),['CODE']);assert.deepEqual(entryOptions('BOTH'),['IMPORT','CODE']);
});

test('manual windows use server time, close at the exact deadline and require a fresh grant to reopen',()=>{
 const window=new ManualSubmissionWindow(),t={status:'REGISTRATION',submissionWindowOpen:true,serverNow:'2026-10-25T10:00:00Z',botPolicy:{submissionDeadline:'2026-10-25T10:00:10Z'}};
 window.update(t,100);assert.equal(window.writable(10099),true);assert.equal(window.writable(10100),false);
 window.update({...t,submissionWindowOpen:false},100);assert.equal(window.writable(100),false);
 window.update({...t,status:'CANCELLED'},100);assert.equal(window.writable(100),false);
 window.update({...t,serverNow:'invalid'},100);assert.equal(window.writable(100),false);
 window.update({...t,serverNow:'2026-10-25T10:00:05Z',botPolicy:{submissionOpenAt:'2026-10-25T10:00:20Z'}},100);assert.equal(window.writable(100),false);
});
