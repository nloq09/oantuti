import test from 'node:test';import assert from 'node:assert/strict';
import {mountCreateWizard} from '../js/tournament/tournament-create-wizard.js';
import {setupPage,wizardNodes} from './helpers/tournament-page-dom.js';
test('wizard validates each next step, keeps data and only shows final create on confirmation',()=>{
 setupPage();const nodes=wizardNodes();let valid=false,reviews=0;const wizard=mountCreateWizard({...nodes,validateStep:()=>valid,review:()=>reviews++,onSubmit:()=>{}});nodes.form.draft='kept';assert.equal(nodes.submit.hidden,true);assert.equal(wizard.advance(),false);assert.equal(wizard.step,0);valid=true;for(let i=0;i<5;i++)wizard.advance();assert.equal(wizard.step,5);assert.equal(nodes.submit.hidden,false);assert.equal(nodes.next.hidden,true);assert.equal(reviews,1);nodes.back.onclick();assert.equal(wizard.step,4);assert.equal(nodes.form.draft,'kept');assert.equal(nodes.panels.filter(p=>!p.hidden).length,1);
});
test('editing an earlier step locks future jumps and final submit returns to the first invalid step',async()=>{
 setupPage();const nodes=wizardNodes();let invalid=-1,calls=0;const wizard=mountCreateWizard({...nodes,validateStep:i=>i!==invalid,onSubmit:()=>calls++});for(let i=0;i<5;i++)wizard.advance();nodes.back.onclick();for(const fn of nodes.form.listeners.input)fn();assert.equal(nodes.stepper.children[5].children[0].disabled,true);wizard.advance();invalid=1;await nodes.form.onsubmit({preventDefault(){}});assert.equal(wizard.step,1);assert.equal(calls,0);assert.equal(nodes.stepper.children[5].children[0].disabled,true);assert.equal(nodes.panels[1].hidden,false);
});
