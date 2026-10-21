import test from 'node:test';import assert from 'node:assert/strict';
import {mountTournamentTabs} from '../js/tournament/tournament-tabs.js';
import {setupPage,Element} from './helpers/tournament-page-dom.js';
test('discover is default and tab clicks show exactly one full-width panel without losing form state',()=>{
 setupPage();const buttons={discover:new Element('button'),create:new Element('button')},panels={discover:new Element('section'),create:new Element('section')};const tabs=mountTournamentTabs(buttons,panels);try{assert.equal(tabs.active,'discover');assert.equal(panels.create.hidden,true);panels.create.draft='Kept';buttons.create.onclick();assert.equal(panels.discover.hidden,true);assert.equal(panels.create.hidden,false);assert.equal(buttons.create.attributes['aria-selected'],'true');buttons.discover.onclick();assert.equal(panels.create.hidden,true);assert.equal(panels.create.draft,'Kept');}finally{tabs.stop();}
});
test('main tabs support arrows home end and roving keyboard focus',()=>{
 setupPage();const buttons={discover:new Element('button'),create:new Element('button')},panels={discover:new Element('section'),create:new Element('section')},tabs=mountTournamentTabs(buttons,panels);try{buttons.discover.onkeydown({key:'ArrowRight',preventDefault(){}});assert.equal(document.activeElement,buttons.create);assert.equal(buttons.discover.tabIndex,-1);buttons.create.onkeydown({key:'Home',preventDefault(){}});assert.equal(tabs.active,'discover');buttons.discover.onkeydown({key:'End',preventDefault(){}});assert.equal(tabs.active,'create');}finally{tabs.stop();}
});
