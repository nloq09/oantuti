// Keep a stable, explicitly identified control focused across a read-only repaint.
export function rememberTournamentFocus(root){
 const active=document.activeElement;
 if(!active||!root.contains(active))return ()=>{};
 const {id}=active;
 return ()=>{const next=root.contains(active)?active:id?document.getElementById(id):null;if(next&&root.contains(next)&&!next.disabled)next.focus({preventScroll:true});};
}
