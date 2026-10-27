export * from './player-labels.js';
import {presentTournamentAudit} from './audit-presentation.js';
export const mapAuditAction=(event,tournament)=>presentTournamentAudit(event,tournament);
