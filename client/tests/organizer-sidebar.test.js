import test from 'node:test';import assert from 'node:assert/strict';
import {installDocument,descendants,text} from './helpers/organizer-dom.js';
import {renderOrganizerSidebar,mountOrganizerShell} from '../js/tournament/organizer-shell.js';
import {organizerGroups,organizerNavigation} from '../js/tournament/organizer-shell-model.js';
import {readFileSync} from 'node:fs';import {cup} from './helpers/organizer-fixtures.js';
const html=readFileSync(new URL('../tournament.html',import.meta.url),'utf8');
test('sidebar separates overview players competition and governance without losing routes',()=>{
 const doc=installDocument('<body><nav></nav></body>'),root=doc.querySelector('nav'),t=cup({timeline:{},format:'SINGLE_ELIMINATION'});renderOrganizerSidebar(root,t,()=>{},{phase:'VALIDATING'});
 assert.deepEqual(root.children.map(n=>n.dataset.group),organizerGroups.map(g=>g.key));assert.equal(descendants(root).filter(n=>n.tag==='a').length,8);
 for(const group of root.children){assert.ok(group.querySelector('h2'));assert.ok(group.querySelector('a'));}
 assert.equal(root.querySelector('[data-section=validation]').href,'/tournament-final-validation.html?id=cup%2Fa');assert.doesNotMatch(text(root),/worker credential|registration token/);
 assert.equal(organizerNavigation(cup({participantPolicy:'HUMAN_ONLY'})).find(n=>n.key==='bracket').label,'Hạt giống');
});
test('actual organizer page mounts with a dedicated check-in panel and accessible active navigation',()=>{
 const doc=installDocument(html);globalThis.location=new URL('http://example.test/tournament.html?id=cup');globalThis.matchMedia=()=>({matches:false,addEventListener(){}});globalThis.addEventListener=globalThis.removeEventListener=()=>{};
 const main=doc.querySelector('main'),t=cup({status:'CHECK_IN',checkInRequired:true}),shown=[];const shell=mountOrganizerShell(main,{tournament:t,account:{id:'owner'},selectPanel:panel=>shown.push(panel)});
 try{assert.ok(shell);shell.select('check-in');assert.equal(shown.at(-1),'organizer-check-in');assert.equal(main.querySelector('[data-section=check-in]').getAttribute('aria-current'),'page');assert.equal(main.querySelector('[data-section=settings]').getAttribute('aria-current'),null);assert.equal(main.querySelectorAll('.organizer-nav-group').length,4);}finally{shell.stop();}
});
