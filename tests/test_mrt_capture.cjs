const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const events = [];
const intervals = [];
const timeouts = [];
const text = { value:'', dispatchEvent(){}, classList: {contains(){return false;}} };
const btn = {playing:false, listeners:{}, classList:{contains(c){return c==='state-playing'&&btn.playing}}, addEventListener(k,cb){this.listeners[k]=cb}, click(){if(this.listeners.click)this.listeners.click();this.playing=!this.playing}};
const pause = {addEventListener(){}, click(){}};
class FakeAudioContext {decodeAudioData(a){this.lastBytes=a.byteLength;return Promise.resolve('ok')}}
const context={
  window:{AudioContext:FakeAudioContext,RsvpMrtBridge:{postMessage(s){events.push(JSON.parse(s))}},app:{setText(v){text.value=v}}},
  document:{readyState:'complete', getElementById(id){return id==='textSpeaker'?btn:id==='textarea'?text:id==='speakerPause'?pause:null}},
  setInterval(fn){intervals.push(fn); return 1;},
  setTimeout(fn){timeouts.push(fn); return timeouts.length;},
  clearTimeout(){},
  HTMLTextAreaElement:function(){},
  Event:class{constructor(type){this.type=type}},
  Reflect,WeakSet,Object,String,Array,JSON
};
context.window.window=context.window;
context.HTMLTextAreaElement.prototype={set value(v){text.value=v}};
Object.defineProperty(context.HTMLTextAreaElement.prototype,'value',{set(v){text.value=v},get(){return text.value}});
vm.createContext(context);
vm.runInContext(fs.readFileSync('app/src/main/assets/mrt_online_capture.js','utf8'),context);
const state=context.window.__rsvpMrt;
assert.ok(state && state.setText('Привет мир!'));
assert.equal(text.value, 'Привет мир!');
const audio=new context.window.AudioContext();
audio.decodeAudioData(new Uint8Array(1500).buffer);
assert.equal(state.encodedChunks,1);
assert.equal(audio.lastBytes,1500);
intervals[0]();
assert.ok(events.some(x=>x.type==='chunk'));
assert.equal(state.startPlayback(),true);
intervals[0]();
assert.ok(events.some(x=>x.type==='started'));
btn.playing=false;
intervals[0]();
assert.ok(timeouts.length);
timeouts[timeouts.length-1]();
assert.ok(events.some(x=>x.type==='finished'));
console.log('PASS: JS hook and original decoder, engine text sync, lifecycle');
