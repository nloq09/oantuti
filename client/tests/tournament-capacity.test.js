import test from 'node:test';
import assert from 'node:assert/strict';
import {participantLimitForFormat} from '../js/tournament/capacity.js';
test('large linear formats allow 4000 entrants while round robin and group formats remain bounded',()=>{for(const format of ['SINGLE_ELIMINATION','SWISS'])assert.equal(participantLimitForFormat(format),4000);for(const format of ['ROUND_ROBIN','GROUP_KNOCKOUT','TEAM_ROUND_ROBIN'])assert.equal(participantLimitForFormat(format),32);});
