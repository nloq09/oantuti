import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const controllers=path.join(root,'server/src/main/java/com/rpsbattle/api/controller');

// This is an inventory of the repository's simple Spring mapping annotations,
// not a Java parser or a security-policy inference engine. Unsupported mapping
// syntax fails explicitly instead of silently publishing an incomplete list.
function annotationPaths(args){
 if(!args.trim())return [''];
 let expression=args.match(/(?:^|,)\s*(?:value|path)\s*=\s*(\{[^}]*\}|"(?:\\.|[^"\\])*")/s)?.[1];
 if(expression===undefined){
  expression=args.match(/^\s*(\{[^}]*\}|"(?:\\.|[^"\\])*")/s)?.[1];
 }
 if(expression===undefined){
  if(/(?:value|path)\s*=/.test(args))throw new Error('Unsupported mapping path: '+args);
  return ['']; // e.g. consumes without a path
 }
 return [...expression.matchAll(/"((?:\\.|[^"\\])*)"/g)].map(match=>JSON.parse('"'+match[1]+'"'));
}

export function documentationRoutes(){
 const routes=[];
 for(const file of fs.readdirSync(controllers).filter(file=>file.endsWith('.java')).sort()){
  const source=fs.readFileSync(path.join(controllers,file),'utf8');
  const classAt=source.search(/public\s+class\s+/);
  if(classAt<0)throw new Error('Unsupported controller declaration: '+file);
  const mappings=[...source.matchAll(/@(Request|Get|Post|Put|Patch|Delete)Mapping\b(?:\(((?:[^()"']|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')*)\))?/g)];
  const base=mappings.filter(mapping=>mapping.index<classAt);
  if(base.length>1||base.some(mapping=>mapping[1]!=='Request'))throw new Error('Unsupported class mappings: '+file);
  const prefixes=base.length?annotationPaths(base[0][2]||''):[''];
  for(const mapping of mappings.filter(mapping=>mapping.index>classAt)){
   if(mapping[1]==='Request')throw new Error('Explicit method mapping required: '+file);
   for(const prefix of prefixes)for(const suffix of annotationPaths(mapping[2]||'')){
    const route=prefix+suffix;
    if(!route.startsWith('/'))throw new Error('Invalid route: '+file+' '+route);
    routes.push({method:mapping[1].toUpperCase(),path:route,file,line:source.slice(0,mapping.index).split('\n').length});
   }
  }
 }
 return routes.sort((a,b)=>a.path.localeCompare(b.path,'en')||a.method.localeCompare(b.method,'en'));
}

export function routeInventory(){
 const routes=documentationRoutes();
 return `${routes.length} mappings khai báo trong các API controller (bao gồm redirect UI; không bao gồm Actuator và WebSocket). Auth/guard không được suy ra từ annotation.\n\n| Method | Path | Nguồn contract |\n| --- | --- | --- |\n`+
  routes.map(route=>`| ${route.method} | \`${route.path}\` | [${route.file.replace('.java','')}:${route.line}](../../server/src/main/java/com/rpsbattle/api/controller/${route.file}#L${route.line}) |`).join('\n');
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const file=path.join(root,'docs/technical/api.md');
 const document=fs.readFileSync(file,'utf8');
 const block=/<!-- ROUTES:START -->\r?\n[\s\S]*?\r?\n<!-- ROUTES:END -->/;
 if(!block.test(document))throw new Error('Missing route inventory markers');
 if(process.argv.includes('--write')){
  fs.writeFileSync(file,document.replace(block,'<!-- ROUTES:START -->\n'+routeInventory()+'\n<!-- ROUTES:END -->'));
  console.log(`Updated ${documentationRoutes().length} route mappings in docs/technical/api.md`);
 }else console.log(routeInventory());
}
