const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const notifications = [];
const intervals = [];
const btn = {
  playing: false,
  classList: {contains(value){return value==='state-playing'&&btn.playing;}},
  addEventListener(type,listener){this.listeners[type] = listener;},
  listeners: {},
  click(){if(this.listeners.click)this.listeners.click();this.playing = !this.playing;}
};
const input={value:'',dispatchEvent(){}};
const pause={addEventListener(){},click(){}};
class FakeAudioContext{
  constructor(){this.state='running';}
  decodeAudioData(buffer){return Promise.resolve(buffer.byteLength)}
  suspend(){this.state='suspended';return Promise.resolve();}
}
const ctx={
  window:{AudioContext:FakeAudioContext, RsvpMrtBridge:{postMessage(event){notifications.push(JSON.parse(event));}}, app:{setText(){}}},
  document:{readyState:'complete',getElementById(id){return id==='textSpeaker'?btn:id==='textarea'?input:id==='speakerPause'?pause:null;}},
  HTMLTextAreaElement:function(){},
  Event:class{},
  setInterval(fn){intervals.push(fn);},
  setTimeout(){return 1;},clearTimeout(){},
  Reflect,WeakSet,Set,Object,String,Array,JSON
};
ctx.window.window=ctx.window;
Object.defineProperty(ctx.HTMLTextAreaElement.prototype,'value',{get(){return input.value;},set(v){input.value=v;}});
vm.createContext(ctx);
vm.runInContext(fs.readFileSync('app/src/main/assets/mrt_online_capture.js','utf8'),ctx);
const mrt=ctx.window.__rsvpMrt;
assert.equal(mrt.prepareStart(),1,'ready to begin via native WebView touch');
assert.equal(btn.playing,false,'prepareStart does not issue an untrusted click');
btn.click(); intervals[0]();
assert.equal(mrt.prepareStart(),2,'already playing');
assert.equal(mrt.prepareStop(),1,'stop needs real WebView touch');
btn.click(); intervals[0]();
assert.equal(btn.playing,false,'native click stopped playback');
const audio=new ctx.window.AudioContext();
assert.equal(mrt.prepareStart(),1);
assert.equal(mrt.forceSilence(),true);
assert.equal(audio.state,'suspended','fallback stops audio contexts');
assert.ok(notifications.some(x=>x.type==='started'));
console.log('PASS: native-touch control bridge and fallback silence');
