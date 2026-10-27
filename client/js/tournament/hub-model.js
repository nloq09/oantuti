import {tournamentNavigation} from './next-action.js';
export {hubTabs} from './discovery-model.js';
export const tournamentCta=(t,context)=>tournamentNavigation(t,context).label;
export const tournamentHref=(t,context)=>tournamentNavigation(t,context).href;
