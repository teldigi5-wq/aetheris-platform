"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const source=fs.readFileSync(path.resolve(__dirname,"../apps/syntra-desktop/ui/local-voice-output.js"),"utf8");

function harness(options={}){
  const events={};
  const mk=(value="")=>({value,checked:false,textContent:"",listeners:{},addEventListener(n,cb){this.listeners[n]=cb;},
    act(n){return this.listeners[n]();}});
  const els={
    "syntra-voice-enable":mk(),
    "syntra-voice-text":mk("Hello from a local voice."),
    "syntra-voice-speak":mk(),
    "syntra-voice-stop":mk(),
    "syntra-voice-status":mk()
  };
  const calls={spoken:[],cancelled:0};
  const synth={getVoices(){return options.voices===undefined?[{localService:true,lang:"en-US",name:"Synthetic local voice"}]:options.voices;},
    speak(u){if(options.failSpeak)throw new Error("PRIVATE_PATH/secret.txt");calls.spoken.push(u);},
    cancel(){calls.cancelled++;}};
  function SpeechSynthesisUtterance(t){this.text=t;this.rate=0;this.pitch=0;this.volume=0;}
  const window=options.unavailable?{}:{speechSynthesis:synth,SpeechSynthesisUtterance};
  const document={hidden:false,getElementById(id){return els[id]||null;},
    addEventListener(n,fn){events[n]=fn;}};
  vm.runInNewContext(source,{document,window});
  return {els,calls,document,events,enable(){els["syntra-voice-enable"].checked=true;els["syntra-voice-enable"].act("change");},
    speak(){els["syntra-voice-speak"].act("click");},stop(){els["syntra-voice-stop"].act("click");},
    status(){return els["syntra-voice-status"].textContent}};
}
test("opt-in required and no automatic voice output on page load",()=>{
  const x=harness();
  assert.equal(x.calls.spoken.length,0);
  x.speak();
  assert.match(x.status(),/Enable local voice first/);
  assert.equal(x.calls.spoken.length,0);
});
test("successful local voice speaks only typed text and supports stop",()=>{
  const x=harness();
  x.enable();x.speak();
  assert.equal(x.calls.spoken.length,1);
  const spoken=x.calls.spoken[0];
  assert.equal(spoken.text,"Hello from a local voice.");
  assert.equal(spoken.voice.localService,true);
  assert.equal(spoken.volume,1);
  spoken.onstart();
  assert.match(x.status(),/Speaking/);
  x.stop();
  assert.match(x.status(),/stopped/);
  spoken.onend();
  assert.match(x.status(),/stopped/,"old completed speech must not override stopped status");
});
test("remote voices are never selected and no cloud fallback occurs",()=>{
  const x=harness({voices:[{name:"Remote",localService:false,lang:"en-US"}]});
  x.enable();
  assert.match(x.status(),/No verified local/);
  x.speak();
  assert.equal(x.calls.spoken.length,0);
});
test("no system TTS engine means no speech and truthful status",()=>{
  const x=harness({unavailable:true});
  x.enable();x.speak();
  assert.equal(x.calls.spoken.length,0);
  assert.match(x.status(),/unavailable/);
});
test("empty, nonprintable or oversized content fails closed",()=>{
  const x=harness();
  x.enable();
  for(const text of ["","   ","hi\nworld","x".repeat(301)]){
    x.els["syntra-voice-text"].value=text;x.speak();
    assert.equal(x.calls.spoken.length,0);
  }
  assert.match(x.status(),/1 and 300 printable/);
});
test("voice disabling immediately cancels and backgrounding mutes",()=>{
  const x=harness();
  x.enable();x.speak();
  x.els["syntra-voice-enable"].checked=false;
  x.els["syntra-voice-enable"].act("change");
  assert.match(x.status(),/disabled/);
  assert.ok(x.calls.cancelled>=2);
  x.enable();x.speak();x.document.hidden=true;x.events.visibilitychange();
  assert.match(x.status(),/stopped/);
});
test("TTS failures cannot leak diagnostic error details",()=>{
  const x=harness({failSpeak:true});x.enable();x.speak();
  assert.equal(x.calls.spoken.length,0);
  assert.match(x.status(),/could not start/);
  assert.doesNotMatch(x.status(),/PRIVATE_PATH|secret/i);
});
test("code never reads microphone, sends network data or persists sensitive speech",()=>{
  for(const forbidden of ["getUserMedia","SpeechRecognition","webkitSpeechRecognition",
    "fetch(","XMLHttpRequest","WebSocket","EventSource","localStorage","sessionStorage",
    "navigator.mediaDevices","__TAURI__","Authorization","console.log","innerHTML","eval("]){
    assert.equal(source.includes(forbidden),false,forbidden);
  }
  assert.match(source,/localService === true/);
  assert.match(source,/document\.hidden/);
});
