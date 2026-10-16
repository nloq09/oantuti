export const busyWorkspace=w=>['BUILDING','TESTING'].includes(w?.status);
export const sourceFilename=language=>({PYTHON:'main.py',CPP:'main.cpp',JAVA:'Main.java'})[language];
export class DraftSession {
 constructor(workspace){this.accept(workspace);}
 accept(workspace){this.revision=workspace.revision;this.server=workspace.sourceCode;this.local=workspace.sourceCode;this.remote=null;}
 get dirty(){return this.local!==this.server;}
 snapshot(){return {revision:this.revision,sourceCode:this.local};}
 saved(request,response){if(request.revision!==this.revision)throw Error('WORKSPACE_CONFLICT');this.revision=response.revision;this.server=request.sourceCode;}
 conflict(remote){this.remote=remote;}
 resolve(local){if(!this.remote)return;this.revision=this.remote.revision;this.server=this.remote.sourceCode;if(!local)this.local=this.server;this.remote=null;}
}
export function parseTestResult(value){if(!value)return null;try{const result=JSON.parse(value);return result&&typeof result.passed==='boolean'?result:null;}catch{return null;}}
export function canTest(workspace,source){return !busyWorkspace(workspace)&&Boolean(workspace?.version?.artifactHash)&&workspace.version.sourceCode===source;}
