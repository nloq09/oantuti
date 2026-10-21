export const tournamentTabNames=['discover','create'];
export function tournamentTab(url){const value=new URL(url,'http://localhost');return tournamentTabNames.includes(value.searchParams.get('tab'))?value.searchParams.get('tab'):tournamentTabNames.includes(value.hash.slice(1))?value.hash.slice(1):'discover';}
export function tournamentTabUrl(url,tab){const value=new URL(url,'http://localhost');value.searchParams.set('tab',tournamentTabNames.includes(tab)?tab:'discover');if(tournamentTabNames.includes(value.hash.slice(1)))value.hash='';return value.pathname+value.search+value.hash;}
