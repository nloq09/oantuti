import {node} from './player-dom.js';

// Player views may be mounted either on a bare page or inside an existing card.
// Keep semantic sections in both cases, but draw only the outer surface.
export function hasPlayerSurface(root){
 for(let parent=root;parent;parent=parent.parentElement)if(parent.matches?.('.panel,.player-detail-section,[data-player-frame]'))return true;
 return false;
}
export function playerSurface(root,css,{flat=false,tag='section'}={}){
 const framed=!flat&&!hasPlayerSurface(root);
 const section=node(tag,undefined,(framed?'panel':'player-content-section')+' '+css);
 if(framed)section.dataset.playerFrame='true';
 return section;
}
