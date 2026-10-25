export function workerRunCommand({origin=location.origin,platform='powershell',name='LAB-PC-01',maxMatches=1,stateDir='./worker-state'}={}){
 const server=new URL(origin);
 if(!['https:','http:'].includes(server.protocol)||server.username||server.password||server.search||server.hash||server.pathname!=='/')throw Error('INVALID_WORKER_SERVER');
 const parts=server.hostname.split('.').map(Number),privateIp=/^\d+\.\d+\.\d+\.\d+$/.test(server.hostname)&&parts.every(n=>n>=0&&n<=255)&&(parts[0]===10||parts[0]===127||parts[0]===192&&parts[1]===168||parts[0]===172&&parts[1]>=16&&parts[1]<=31);
 if(server.port==='0'||server.protocol==='http:'&&!['localhost','[::1]'].includes(server.hostname)&&!privateIp)throw Error('WORKER_HTTPS_REQUIRED');
 if(!/^[\p{L}\p{N} _.-]{1,80}$/u.test(name)||!name.trim())throw Error('INVALID_WORKER_NAME');
 const matches=Number(maxMatches);if(!Number.isInteger(matches)||matches<1||matches>256)throw Error('INVALID_WORKER_CAPACITY');
 if(!['powershell','bash'].includes(platform))throw Error('INVALID_WORKER_PLATFORM');
 if(typeof stateDir!=='string'||!/^\.\/[A-Za-z0-9_-]{1,100}$/.test(stateDir))throw Error('INVALID_WORKER_STATE_DIRECTORY');
 return "java -jar rps-worker.jar --central="+server.origin+" --name='"+name+"' --state-dir="+stateDir+" --max-matches="+matches+" --max-bots="+(matches*2)+" --serve"+(server.protocol==='http:'?' --allow-insecure-http':'');
}
export function workerFirstRunCommand(options={}){
 const run=workerRunCommand(options);
 if((options.platform||'powershell')==='bash')return "(\n  read -r -s -p 'Registration token: ' WORKER_REGISTRATION_TOKEN\n  printf '\\n'\n  export WORKER_REGISTRATION_TOKEN\n  "+run+"\n)";
 return "$env:WORKER_REGISTRATION_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Registration token' -AsSecureString)).Password\ntry {\n    "+run+"\n} finally {\n    Remove-Item Env:WORKER_REGISTRATION_TOKEN -ErrorAction SilentlyContinue\n}";
}
export function workerSetupCommand(origin=location.origin,_protocol=location.protocol,options={}){
 return workerFirstRunCommand({...options,origin});
}
