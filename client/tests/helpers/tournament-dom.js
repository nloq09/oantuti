export class Element {
 constructor(tag){this.tag=tag;this.children=[];this.dataset={};this.attributes={};this.textContent='';this.disabled=false;}
 append(...nodes){this.children.push(...nodes);}
 setAttribute(key,value){this.attributes[key]=value;}
}
export function setup(){globalThis.document={createElement:tag=>new Element(tag)};return new Element('main');}
export const descendants=e=>[e,...e.children.filter(x=>x instanceof Element).flatMap(descendants)];
