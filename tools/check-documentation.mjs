import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {routeInventory} from './documentation-routes.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
function markdownFiles(directory){
 return fs.readdirSync(directory,{withFileTypes:true}).flatMap(entry=>{
  const target=path.join(directory,entry.name);
  return entry.isDirectory()?markdownFiles(target):entry.name.endsWith('.md')?[target]:[];
 });
}
function exactCase(target){
 const relative=path.relative(root,target);
 if(relative==='')return true;
 if(relative.startsWith('..'+path.sep)||path.isAbsolute(relative))return false;
 let current=root;
 for(const part of relative.split(path.sep)){
  if(!fs.readdirSync(current).includes(part))return false;
  current=path.join(current,part);
 }
 return true;
}
const files=[path.join(root,'README.md'),...markdownFiles(path.join(root,'docs'))];
const groups={
 player:['getting-started','game-rules','how-to-play','tournament-flow','faq'],
 bot:['bot-guide','protocol','examples','build-test-submit','limits','troubleshooting'],
 tournament:['formats','registration','coding-phase','validation','check-in','bracket-ranking'],
 organizer:['create-tournament','registration-management','submission-management','validation-management','tournament-operation','match-monitor','audit'],
 admin:['users','bots','workers','security','system-operations'],
 technical:['architecture','api','websocket','worker-protocol','database','deployment','monitoring','backup-recovery']
};
const guides=Object.entries(groups).flatMap(([group,names])=>names.map(name=>`docs/${group}/${name}.md`));
for(const guide of guides)assert.ok(fs.existsSync(path.join(root,guide)),`Missing required guide: ${guide}`);
const allowedTopLevel=new Set(['README.md','glossary.md','DOCUMENTATION-COVERAGE.md']);
for(const entry of fs.readdirSync(path.join(root,'docs'),{withFileTypes:true})){
 if(entry.isFile()&&entry.name.endsWith('.md'))assert.ok(allowedTopLevel.has(entry.name),`Unsorted legacy Markdown at docs root: ${entry.name}`);
}
const api=fs.readFileSync(path.join(root,'docs/technical/api.md'),'utf8').replaceAll('\r\n','\n');
const inventory=/<!-- ROUTES:START -->\n([\s\S]*?)\n<!-- ROUTES:END -->/.exec(api);
assert.ok(inventory,'API route inventory is missing');
assert.equal(inventory[1],routeInventory(),'API mapping inventory is stale: run node tools/documentation-routes.mjs --write');
let links=0;
for(const file of files){
 const content=fs.readFileSync(file,'utf8').replace(/```[^\n]*\n[\s\S]*?```/g,'');
 for(const match of content.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)){
  const href=match[1];
  if(/^(?:https?:|mailto:|#)/.test(href))continue;
  const target=path.resolve(path.dirname(file),decodeURIComponent(href.split('#')[0]));
  assert.ok(fs.existsSync(target),`${path.relative(root,file)}: missing target ${href}`);
  assert.ok(exactCase(target),`${path.relative(root,file)}: wrong path case or outside repository: ${href}`);
  links++;
 }
}
const examples=fs.readFileSync(path.join(root,'docs/bot/examples.md'),'utf8').replaceAll('\r\n','\n');
for(const [language,filename]of [['python','main.py'],['cpp','main.cpp'],['java','Main.java']]){
 const block=new RegExp('```'+language+'\\r?\\n([\\s\\S]*?)\\r?\\n```').exec(examples);
 assert.ok(block,`Missing ${language} example`);
 assert.equal(block[1].trim(),fs.readFileSync(path.join(root,'client/assets/bot-examples',filename),'utf8').replaceAll('\r\n','\n').trim(),`Documented ${filename} differs from the verified repository starter`);
}
const readJsonLine=filename=>{
 const lines=fs.readFileSync(path.join(root,'docs/bot/examples',filename),'utf8').trim().split(/\r?\n/);
 assert.equal(lines.length,1,`${filename} must contain one complete JSON Line`);
 return JSON.parse(lines[0]);
};
const request=readJsonLine('turn.jsonl'),response=readJsonLine('response.jsonl');
assert.equal(request.type,'YOUR_TURN');assert.equal(response.type,'ACTION');
assert.equal(response.sequence,request.sequence);assert.deepEqual(response.action,request.legalActions[0]);
console.log(`${guides.length} required guides, ${files.length} Markdown files, ${links} local links/images, API route inventory, 3 embedded bot examples and JSON Lines: PASS`);
