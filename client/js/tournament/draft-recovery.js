export function temporaryDraft(draft){return JSON.stringify({format:1,revision:draft.revision,server:draft.server,sourceCode:draft.local});}
export function readTemporary(raw){if(raw===null)return null;try{const value=JSON.parse(raw);if(value?.format===1&&typeof value.sourceCode==='string'&&Number.isSafeInteger(value.revision))return value;}catch{}return {sourceCode:raw,revision:null,server:null};}
/** Compare both revision and base source; this function never uploads or discards local edits. */
export function reconcileDraft(draft,remote){if(draft.dirty){if(remote.revision!==draft.revision||remote.sourceCode!==draft.server){draft.conflict(remote);return 'conflict';}return 'local';}draft.accept(remote);return 'server';}

export function autosaveDelay(key){let hash=2166136261;for(const char of String(key))hash=Math.imul(hash^char.charCodeAt(0),16777619)>>>0;return 2000+hash%3001;}
