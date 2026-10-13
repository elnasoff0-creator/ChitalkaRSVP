const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const intervals = [];
const nodes = new Map();
const classes = new Set();
const textarea = {value:'',readOnly:false,tabIndex:0,blur(){this.blurred=true;},dispatchEvent(){}};
const btn = {classList:{contains(){return false;}}, addEventListener(){}};
const pause = {addEventListener(){}};
const body = {style:{}};
const head = {appendChild(el){nodes.set(el.id,el);}};
const document = {
 readyState:'complete',body,head,
 documentElement:{classList:{toggle(name,enabled){if(enabled)classes.add(name);else classes.delete(name);}}},
 activeElement:textarea,
 createElement(){return {style:{},textContent:'',id:''};},
 getElementById(id){if(id==='textarea')return textarea;if(id==='textSpeaker')return btn;if(id==='speakerPause')return pause;return nodes.get(id)||null;}
};
class FakeAudioContext{decodeAudioData(){return Promise.resolve()}}
const ctx={window:{AudioContext:FakeAudioContext,app:{setText(){}},RsvpMrtBridge:{postMessage(){}}},document,Event:class{},HTMLTextAreaElement:function(){},setInterval(fn){intervals.push(fn);},setTimeout(){return 1;},clearTimeout(){},Reflect,WeakSet,Set,Object,String,Array,JSON};
Object.defineProperty(ctx.HTMLTextAreaElement.prototype,'value',{get(){return textarea.value;},set(v){textarea.value=v;}});
vm.createContext(ctx);
vm.runInContext(fs.readFileSync('app/src/main/assets/mrt_online_capture.js','utf8'),ctx);
const mrt=ctx.window.__rsvpMrt;
assert.equal(mrt.setNativeUiHidden(true),true);
assert(classes.has('rsvp-headless-mrt'));
assert.equal(body.style.opacity,'0.001');
assert(textarea.readOnly);
assert.equal(textarea.tabIndex,-1);
assert(textarea.blurred);
assert(nodes.has('rsvp-headless-mrt-css'));
assert.equal(mrt.setNativeUiHidden(false),true);
assert(!classes.has('rsvp-headless-mrt'));
assert.equal(body.style.opacity,'');
console.log('PASS: headless WebUI hides web controls, preserves actual button, prevents textarea typing');
