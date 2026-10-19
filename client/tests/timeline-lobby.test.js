import test from 'node:test';
import assert from 'node:assert/strict';
import {timelineLobbyDestination,timelineStartText} from '../js/tournament/timeline-lobby.js';
import {currentHumanMatch} from '../js/tournament/match-entry.js';
test('timeline enters the actual running lobby and encodes the ID',()=>{assert.equal(timelineLobbyDestination('/tournament-detail.html','a/b','RUNNING'),'/tournament-lobby.html?id=a%2Fb');assert.equal(timelineLobbyDestination('/tournament-lobby.html','a','RUNNING'),null);});
test('clock phases cannot trigger navigation before the server actually started',()=>{for(const phase of ['CHECK_IN_OPEN','READY_TO_START','VALIDATING','CODING_OPEN','FINISHED','CANCELLED','PAUSED','RECOVERY_REQUIRED'])assert.equal(timelineLobbyDestination('/tournament-detail.html','cup',phase),null);});
test('start issues distinguish queue backpressure, infrastructure and insufficient roster',()=>{assert.match(timelineStartText('SERVER_BUSY'),/tự thử/);assert.match(timelineStartText('BOT_SANDBOX_UNAVAILABLE'),/Môi trường/);assert.match(timelineStartText('NOT_ENOUGH_CHECKED_IN_BOTS'),/đã hủy/);assert.equal(timelineStartText('private stack trace').includes('private'),false);});
test('bot-controlled participants remain spectators while the existing human entry flow remains available',()=>{const match={id:'m',onlineMatchId:'room'};assert.equal(currentHumanMatch([{registration:{controllerType:'BOT'},status:'PLAYING',currentMatch:match}]),undefined);assert.deepEqual(currentHumanMatch([{registration:{controllerType:'HUMAN'},status:'PLAYING',currentMatch:match}]),match);});
