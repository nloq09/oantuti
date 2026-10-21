import {tournamentTab,tournamentTabUrl,tournamentTabNames} from './tournament-tab-state.js';
export function mountTournamentTabs(buttons,panels,{onChange=()=>{}}={}){
 let active;
 function select(tab,{write=true,focus=false}={}){tab=tournamentTabNames.includes(tab)?tab:'discover';for(const key of tournamentTabNames){buttons[key].setAttribute('aria-selected',String(key===tab));buttons[key].tabIndex=key===tab?0:-1;panels[key].hidden=key!==tab;}if(write)history.pushState(null,'',tournamentTabUrl(location.href,tab));const changed=active!==tab;active=tab;if(focus)buttons[tab].focus();if(changed)onChange(tab);}
 for(const [key,button]of Object.entries(buttons)){button.onclick=()=>select(key);button.onkeydown=event=>{let index=tournamentTabNames.indexOf(key);if(event.key==='ArrowRight'||event.key==='ArrowLeft')index=1-index;else if(event.key==='Home')index=0;else if(event.key==='End')index=1;else return;event.preventDefault();select(tournamentTabNames[index],{focus:true});};}
 const restore=()=>select(tournamentTab(location.href),{write:false});addEventListener('popstate',restore);addEventListener('hashchange',restore);restore();return{select,get active(){return active;},stop(){removeEventListener('popstate',restore);removeEventListener('hashchange',restore);}};
}
