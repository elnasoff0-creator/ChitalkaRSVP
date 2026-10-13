const fs=require('fs'),vm=require('vm'),assert=require('assert');
const events=[]; const timers=[];
class AudioNode {
  constructor(context){this.context=context;this.connections=[];}
  connect(node){this.connections.push(node);return node;}
}
class AudioDestinationNode extends AudioNode{}
class FakeContext{
  constructor(){this.destination=new AudioDestinationNode(this);this.state='running';}
  createGain(){let g=new AudioNode(this);g.gain={value:1};return g;}
  decodeAudioData(buf){return Promise.resolve(buf.byteLength)}
  suspend(){this.state='suspended';return Promise.resolve()}
}
const btn={classList:{contains:()=>false},addEventListener(){}};
const textarea={value:'',dispatchEvent(){},blur(){}};
const ctx={
  window:{AudioNode,AudioDestinationNode,AudioContext:FakeContext,
   RsvpMrtBridge:{postMessage(text){events.push(JSON.parse(text))}},app:{setText(){}}},
  document:{readyState:'complete',getElementById(id){return id==='textSpeaker'?btn:id==='textarea'?textarea:null;}},
  HTMLTextAreaElement:function(){},
  setInterval(fn){timers.push(fn)},setTimeout(fn){return 1},clearTimeout(){},
  Reflect,WeakSet,WeakMap,Set,Object,Array,JSON,Uint8Array,String,
  btoa(input){return Buffer.from(input,'binary').toString('base64')},
  Event:class{}
};
Object.defineProperty(ctx.HTMLTextAreaElement.prototype,'value',{get(){return textarea.value},set(value){textarea.value=value}});
vm.createContext(ctx);
vm.runInContext(fs.readFileSync('app/src/main/assets/mrt_online_capture.js','utf8'),ctx);
const mrt=ctx.window.__rsvpMrt;
assert.strictEqual(mrt.captureBegin(17),true);
const audio=new ctx.window.AudioContext();
const node=new AudioNode(audio);
node.connect(audio.destination);
assert.strictEqual(node.connections[0].gain.value,0,'WebAudio destination is routed through a silent gain');
const source=Buffer.alloc(31000,0xab);
source[0]=0xff;source[1]=0xfb;
const bytes=Uint8Array.from(source).buffer;
audio.decodeAudioData(bytes);
const parts=events.filter(e=>e.type==='audioPart'&&e.session===17);
assert(parts.length>=3, 'encoded audio is sent as small blocks');
const merged=Buffer.concat(parts.map(p=>Buffer.from(p.data,'base64')));
assert.deepStrictEqual(merged,source,'Base64 blocks retain the exact original byte order');
assert(parts.every(p=>p.data.length < 65536));
mrt.captureEnd();
mrt.captureDisable(false);
assert.strictEqual(node.connections[0].gain.value,0,'WebAudio stays silent until explicitly restored');
mrt.captureDisable(true);
assert.strictEqual(node.connections[0].gain.value,1,'legacy playback can restore audible gain');
console.log('PASS: original encoded buffers -> ordered Base64 blocks, muted prefetch, safe legacy restore');
