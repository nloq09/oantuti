// Small deterministic DOM fixture for organizer integration tests; no layout/browser emulation.
export class Element {
 constructor(tag){this.tag=tag.toLowerCase();this.children=[];this.dataset={};this.attributes={};this.textContent='';this.className='';this.hidden=false;this.disabled=false;this.listeners=new Map();this._value=undefined;this.parentElement=null;}
 get childNodes(){return this.children;}get childElementCount(){return this.children.filter(c=>c instanceof Element).length;}
 get options(){return this.children.filter(c=>c.tag==='option');}get value(){return this._value??(this.tag==='select'?this.options[0]?.value:'')??'';}set value(value){this._value=String(value);}
 get isConnected(){return this.tag==='document'||Boolean(this.parentElement?.isConnected);}
 get previousElementSibling(){const siblings=this.parentElement?.children||[];return siblings[siblings.indexOf(this)-1]||null;}
 get classList(){return{add:(...names)=>{this.className=[...new Set([...this.className.split(/\s+/),...names])].filter(Boolean).join(' ');}};}
 append(...nodes){for(const child of nodes){if(child instanceof Element){child.remove();child.parentElement=this;}this.children.push(child);}}
 prepend(...nodes){for(const child of [...nodes].reverse()){if(child instanceof Element){child.remove();child.parentElement=this;}this.children.unshift(child);}}
 replaceChildren(...nodes){for(const child of [...this.children])if(child instanceof Element)child.parentElement=null;this.children=[];this.textContent='';this.append(...nodes);}
 remove(){if(this.parentElement){const parent=this.parentElement;parent.children.splice(parent.children.indexOf(this),1);this.parentElement=null;}}
 before(...nodes){this.insertRelative(false,nodes);}after(...nodes){this.insertRelative(true,nodes);}
 insertRelative(after,nodes){const parent=this.parentElement;if(!parent)return;for(const child of nodes){if(child instanceof Element)child.remove();}const at=parent.children.indexOf(this)+(after?1:0);for(const child of nodes)if(child instanceof Element)child.parentElement=parent;parent.children.splice(at,0,...nodes);}
 setAttribute(key,value){this.attributes[key]=String(value);if(key==='id')this.id=String(value);if(key==='class')this.className=String(value);if(key==='value')this.value=value;if(key==='hidden')this.hidden=true;if(key==='open')this.open=true;if(key.startsWith('data-'))this.dataset[key.slice(5).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())]=String(value);}
 getAttribute(key){return this.attributes[key]??null;}removeAttribute(key){delete this.attributes[key];}
 matches(selector){if(selector==='*')return true;const attrs=[...selector.matchAll(/\[([^=\]]+)(?:=["']?([^\]"']*)["']?)?\]/g)];for(const [,key,value]of attrs){const actual=key.startsWith('data-')?this.dataset[key.slice(5).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())]:this.attributes[key];if(actual===undefined||value!==undefined&&actual!==value)return false;}const plain=selector.replace(/\[[^\]]*\]/g,''),id=plain.match(/#([\w-]+)/)?.[1],classes=[...plain.matchAll(/\.([\w-]+)/g)].map(m=>m[1]),tag=plain.match(/^[\w-]+/)?.[0];return(!id||this.id===id)&&(!tag||this.tag===tag)&&classes.every(c=>this.className.split(/\s+/).includes(c));}
 querySelectorAll(selectors){const result=[];for(const selector of selectors.split(',')){const parts=selector.trim().split(/\s+/);for(const node of descendants(this).slice(1)){if(!node.matches(parts.at(-1)))continue;let parent=node.parentElement,at=parts.length-2;while(at>=0&&parent){if(parent.matches(parts[at]))at--;parent=parent.parentElement;}if(at<0&&!result.includes(node))result.push(node);}}return result;}
 querySelector(selector){return this.querySelectorAll(selector)[0]||null;}contains(child){return descendants(this).includes(child);}
 addEventListener(type,fn){if(!this.listeners.has(type))this.listeners.set(type,new Set());this.listeners.get(type).add(fn);}removeEventListener(type,fn){this.listeners.get(type)?.delete(fn);}
 dispatchEvent(event){for(const listener of this.listeners.get(event.type)||[])listener(event);return true;}click(){return this.onclick?.({preventDefault(){}});}
 focus(){globalThis.document.activeElement=this;}showModal(){this.open=true;}close(){this.open=false;this.dispatchEvent({type:'close'});}
}
export const descendants=root=>[root,...root.children.filter(c=>c instanceof Element).flatMap(descendants)];
export const text=root=>descendants(root).map(n=>n.textContent).join(' ');
export function parseDocument(html){
 const doc=new Element('document'),stack=[doc],voidTags=new Set(['area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr']);
 for(const token of html.match(/<!--[\s\S]*?-->|<[^>]+>|[^<]+/g)||[]){if(token.startsWith('<!'))continue;if(token.startsWith('</')){const tag=token.slice(2).match(/^[\w-]+/)?.[0];while(stack.length>1){if(stack.pop().tag===tag)break;}continue;}if(token.startsWith('<')){const tag=token.slice(1).match(/^[\w-]+/)?.[0];if(!tag)continue;const element=new Element(tag);for(const match of token.slice(tag.length+1,-1).matchAll(/([\w:-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g))element.setAttribute(match[1],match[2]??match[3]??match[4]??'');stack.at(-1).append(element);if(!voidTags.has(tag)&&!token.endsWith('/>'))stack.push(element);}else stack.at(-1).textContent+=token.trim();}
 doc.body=doc.querySelector('body');doc.createElement=tag=>new Element(tag);doc.getElementById=id=>doc.querySelector('#'+id);doc.createTextNode=value=>String(value);doc.activeElement=null;doc.hidden=true;return doc;
}
export function installDocument(html){const doc=parseDocument(html);globalThis.document=doc;globalThis.Node=Element;globalThis.Option=class extends Element{constructor(label,value){super('option');this.textContent=label;this.value=value;}};return doc;}
