import {Element,setup,descendants} from './tournament-dom.js';
export {Element,descendants};
export const tick=()=>new Promise(resolve=>setImmediate(resolve));
export function setupPage(url='http://localhost/tournaments.html'){
 const root=setup(),events=new Map();Element.prototype.replaceChildren=function(...nodes){this.children=[];this.append(...nodes);};Element.prototype.prepend=function(...nodes){this.children.unshift(...nodes);};Element.prototype.contains=function(n){return descendants(this).includes(n);};Element.prototype.removeAttribute=function(key){delete this.attributes[key];};Element.prototype.focus=function(){document.activeElement=this;};Element.prototype.querySelectorAll=function(selector){return descendants(this).slice(1).filter(n=>selector.split(',').includes(n.tag));};Element.prototype.querySelector=function(selector){return this.querySelectorAll(selector)[0];};Element.prototype.addEventListener=function(type,fn){this.listeners||={};(this.listeners[type]||=[]).push(fn);};Element.prototype.isConnected=true;
 globalThis.Node=Element;globalThis.Option=class extends Element{constructor(text,value){super('option');this.textContent=text;this.value=value;}};
 const setUrl=value=>{globalThis.location=new URL(value,globalThis.location?.href||url);};setUrl(url);globalThis.history={pushState(_state,_title,value){setUrl(value);},replaceState(_state,_title,value){setUrl(value);}};
 globalThis.addEventListener=(type,fn)=>{if(!events.has(type))events.set(type,new Set());events.get(type).add(fn);};globalThis.removeEventListener=(type,fn)=>events.get(type)?.delete(fn);document.addEventListener=globalThis.addEventListener;document.removeEventListener=globalThis.removeEventListener;document.hidden=false;
 return{root,events,setUrl};
}
export function wizardNodes(){const form=new Element('form'),panels=Array.from({length:6},()=>{const panel=new Element('section');panel.append(new Element('h3'));form.append(panel);return panel;});return{form,panels,stepper:new Element('ol'),progress:new Element('p'),back:new Element('button'),next:new Element('button'),submit:new Element('button')};}
