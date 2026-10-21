import test from 'node:test';import assert from 'node:assert/strict';
import {tournamentTab,tournamentTabUrl} from '../js/tournament/tournament-tab-state.js';
import {mountTournamentTabs} from '../js/tournament/tournament-tabs.js';
import {setupPage,Element} from './helpers/tournament-page-dom.js';
test('tab URL accepts supported query or hash and preserves discovery query state',()=>{
 for(const [url,expected]of [['/tournaments.html','discover'],['?tab=create','create'],['/tournaments.html#create','create'],['?tab=unknown','discover'],['?tab=discover#create','discover']])assert.equal(tournamentTab(url),expected);
 assert.equal(tournamentTabUrl('/tournaments.html?category=open&q=Chess&page=2#create','discover'),'/tournaments.html?category=open&q=Chess&page=2&tab=discover');
});
test('reload and history restore explicit Create without replacing discovery filters',()=>{
 const env=setupPage('http://localhost/tournaments.html?tab=create&category=open&q=Alpha');const buttons={discover:new Element('button'),create:new Element('button')},panels={discover:new Element('section'),create:new Element('section')},tabs=mountTournamentTabs(buttons,panels);try{assert.equal(tabs.active,'create');tabs.select('discover');assert.equal(new URL(location.href).searchParams.get('q'),'Alpha');env.setUrl('http://localhost/tournaments.html?tab=create&category=open&q=Alpha');for(const fn of env.events.get('popstate'))fn();assert.equal(tabs.active,'create');assert.equal(panels.discover.hidden,true);}finally{tabs.stop();}
});
