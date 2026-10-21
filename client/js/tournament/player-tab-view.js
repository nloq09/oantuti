import {node} from './player-dom.js';

// One active read view: parent repaint may move its host but must not create another poller.
export function createPlayerTabView(){
 let scope=null,host=null,view=null,tournament,participant;
 function clear(){view?.stop();view=null;host=null;scope=null;}
 return{
  reconcile(t,tab,account,phase,me){
   tournament=t;participant=me;const next=JSON.stringify([t.id,tab,account?.id||null,account?.role||null,phase||null]);
   if(next!==scope){clear();scope=next;}
  },
  attach(parent,mount){
   if(!host){host=node('div');parent.append(host);view=mount(host);}
   else {parent.append(host);view?.update?.(tournament,participant);}
   return view;
  },
  clear,
 };
}
