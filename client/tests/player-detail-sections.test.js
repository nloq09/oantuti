import test from 'node:test';
import assert from 'node:assert/strict';
import {createPlayerDetailSections,playerSectionTarget,renderPlayerTournamentInfo} from '../js/tournament/player-detail-sections.js';
import {installDocument,text} from './helpers/organizer-dom.js';
import {node} from '../js/tournament/player-dom.js';

test('related sections mount lazily, preserve open state and keep one live view across revisions',()=>{
 const root=installDocument('<main></main>').querySelector('main'),sections=createPlayerDetailSections();let mounts=0,updates=0,stops=0;
 const paint=revision=>{sections.begin({tournament:{id:'cup',revision},account:{id:'player'},snapshot:String(revision)});root.replaceChildren();sections.attach(root,{id:'matches',title:'Trận đấu',render:body=>{mounts++;body.append(node('p','Matches'));return{update(){updates++;},stop(){stops++;}};}});sections.finish();};
 paint(1);assert.equal(mounts,0);const summary=sections.open('matches');assert.equal(summary.tag,'summary');assert.equal(summary.getAttribute('aria-controls'),'matches-body');assert.equal(mounts,1);
 sections.open('matches');assert.equal(mounts,1);paint(2);assert.equal(root.querySelector('#matches').open,true);assert.equal(mounts,1);assert.ok(updates>0);
 root.querySelector('#matches').open=false;root.querySelector('#matches').dispatchEvent(new Event('toggle'));assert.equal(stops,1);sections.open('matches');assert.equal(mounts,2);
 sections.begin({tournament:{id:'cup',revision:3},account:{id:'other'},snapshot:'3'});assert.equal(stops,2,'account changes stop previous private read views');sections.clear();
});

test('expanded auxiliary forms keep unsent input on polling; explicit successful actions can refresh them',()=>{
 const root=installDocument('<main></main>').querySelector('main'),sections=createPlayerDetailSections();
 const paint=revision=>{sections.begin({tournament:{id:'cup'},snapshot:revision});root.replaceChildren();sections.attach(root,{id:'teams',title:'Đồng đội',render:body=>body.append(node('input'))});sections.finish();};
 paint(1);sections.open('teams');const input=root.querySelector('input');input.value='Local team';root.querySelector('.player-section-body').dispatchEvent(new Event('input'));
 paint(2);assert.equal(root.querySelector('input'),input);assert.equal(input.value,'Local team');assert.equal(root.querySelector('#teams').open,true);
 sections.clearDirty();paint(3);assert.notEqual(root.querySelector('input'),input);assert.equal(root.querySelector('#teams').open,true);
});

test('phase results update without collapsing inner details; a new phase replaces the previous phase view',()=>{
 const root=installDocument('<main></main>').querySelector('main'),sections=createPlayerDetailSections();
 const paint=(revision,phase)=>{sections.begin({tournament:{id:'cup'},snapshot:revision});root.replaceChildren();sections.attach(root,{id:'tournament-phase-content',details:false,identity:phase,render:body=>{body.append(node('p',phase+' '+revision));const details=node('details');details.id='case-details';details.append(node('summary','Chi tiết'));body.append(details);}});sections.finish();};
 paint(1,'VALIDATING');root.querySelector('#case-details').open=true;paint(2,'VALIDATING');assert.match(text(root),/VALIDATING 2/);assert.equal(root.querySelector('#case-details').open,true);
 paint(3,'CHECK_IN_OPEN');assert.match(text(root),/CHECK_IN_OPEN 3/);assert.doesNotMatch(text(root),/VALIDATING/);
});

test('old hashes map to sections while info retains schedule and conditions without repeating the header',()=>{
 for(const [old,target]of [['overview','tournament-info'],['registration','player-registration-panel'],['tournament-registration-form','player-registration-panel'],['rules','tournament-rules'],['validation','tournament-phase-content'],['check-in','tournament-phase-content'],['my-match','my-matches'],['ranking','ranking']])assert.equal(playerSectionTarget(old),target);
 const root=installDocument('<main></main>').querySelector('main');renderPlayerTournamentInfo(root,{name:'Header-only name',format:'GROUP_KNOCKOUT',description:'Giải thực hành',maxParticipants:100,participantPolicy:'BOT_ONLY',checkInRequired:true,botPolicy:{allowedLanguages:['PYTHON'],submissionMode:'BOTH'},registrationPolicy:{approvalPolicy:'HYBRID',requireAffiliationVerification:true},timeline:{codingOpenAt:'2026-10-24T09:00:00Z'},groupPlans:[{groups:2,qualifyTopN:1}]});
 assert.match(text(root),/Giải thực hành/);assert.match(text(root),/xác minh/);assert.match(text(root),/Code bắt đầu/);assert.match(text(root),/2 bảng/);assert.doesNotMatch(text(root),/Header-only name|100 người/);
});
