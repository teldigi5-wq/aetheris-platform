"use strict";

/* Opt-in native WebView speech *output* using locally installed voices only.
 * No microphone, network, storage, automatic speech, AI reply or privileged IPC.
 */
(() => {
  const toggle = document.getElementById("syntra-voice-enable");
  const input = document.getElementById("syntra-voice-text");
  const speakButton = document.getElementById("syntra-voice-speak");
  const stopButton = document.getElementById("syntra-voice-stop");
  const status = document.getElementById("syntra-voice-status");
  if (!toggle || !input || !speakButton || !stopButton || !status) return;
  const synth = window.speechSynthesis;
  const Utterance = window.SpeechSynthesisUtterance;
  let current = 0;

  const setStatus = (value) => { status.textContent = value; };
  const mute = () => {
    current += 1;
    if (synth && typeof synth.cancel === "function") synth.cancel();
    setStatus(toggle.checked ? "Voice stopped. No microphone is active." : "Voice disabled. No microphone is active.");
  };
  const hasEngine = () => Boolean(synth && typeof synth.getVoices === "function"
    && typeof synth.speak === "function" && typeof synth.cancel === "function"
    && typeof Utterance === "function");
  const localVoices = () => {
    if (!hasEngine()) return [];
    try {
      const voices = synth.getVoices();
      return Array.isArray(voices) ? voices.filter((v) => v && v.localService === true) : [];
    } catch (_) { return []; }
  };
  toggle.addEventListener("change", () => {
    mute();
    if (!toggle.checked) return;
    if (!hasEngine()) setStatus("Speech output is unavailable in this WebView. No cloud fallback.");
    else if (!localVoices().length) setStatus("No verified local system voice found. Voice remains blocked.");
    else setStatus("Local speech output enabled. Enter text and press Speak; no listening occurs.");
  });
  speakButton.addEventListener("click", () => {
    if (!toggle.checked) { setStatus("Enable local voice first."); return; }
    if (!hasEngine()) { setStatus("Speech output is unavailable. No cloud fallback."); return; }
    const voices = localVoices();
    if (!voices.length) { setStatus("No verified local system voice found. Nothing was spoken."); return; }
    const text = input.value.trim();
    if (!text || text.length > 300 || /[\u0000-\u001f\u007f]/.test(text)) {
      setStatus("Enter between 1 and 300 printable characters."); return;
    }
    // Stop any previous utterance before creating the next, even if the
    // previous voice is still generating a speech result asynchronously.
    mute();
    const id = current;
    try {
      const utterance = new Utterance(text);
      utterance.voice = voices.find((v) => /^en(?:-|$)/i.test(v.lang || "")) || voices[0];
      utterance.rate = 1;
      utterance.pitch = 1;
      utterance.volume = 1;
      utterance.onstart = () => { if (id === current && toggle.checked) setStatus("Speaking with a local system voice…"); };
      utterance.onend = () => { if (id === current) setStatus("Finished speaking. Ready for another explicit request."); };
      utterance.onerror = () => { if (id === current) setStatus("Local speech output failed. No fallback was used."); };
      synth.speak(utterance);
      setStatus("Speech requested from locally installed voice.");
    } catch (_) {
      if (id === current) setStatus("Speech could not start; no cloud fallback was used.");
    }
  });
  stopButton.addEventListener("click", mute);
  // If the app is backgrounded or hidden, do not continue speaking inadvertently.
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) mute();
  });
})();
