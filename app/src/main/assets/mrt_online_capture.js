/*
 * Chitalka RSVP: original monitor for MRT WebAudio playback.
 * Inspired by the user's MRT Desktop TTS: observe encoded buffers at
 * AudioContext.decodeAudioData before the original decoder consumes them.
 * This edition counts chunks only. It does NOT copy, transfer or save audio.
 * Injected at document start into the trusted MRT origin via AndroidX WebKit.
 */
(function () {
  'use strict';
  if (window.__rsvpMrt && window.__rsvpMrt.version >= 1) return;
  var state = {
    version: 1,
    patched: new WeakSet(),
    contexts: new Set(),
    encodedChunks: 0,
    encodedBytes: 0,
    started: false,
    playing: false,
    pausedByUser: false,
    manuallyStopped: false,
    endTimer: null,
    hookedButton: false,
    originalContext: null
  };

  function notify(type, extra) {
    try {
      if (!window.RsvpMrtBridge || typeof window.RsvpMrtBridge.postMessage !== 'function') return;
      var message = {type: type, chunks: state.encodedChunks, bytes: state.encodedBytes};
      if (extra) Object.keys(extra).forEach(function (k) { message[k] = extra[k]; });
      window.RsvpMrtBridge.postMessage(JSON.stringify(message));
    } catch (ignored) {}
  }

  function patchContext(context) {
    if (!context || state.patched.has(context)) return false;
    var original = context.decodeAudioData;
    if (typeof original !== 'function') return false;
    try {
      context.decodeAudioData = function (arrayBuffer) {
        try {
          if (arrayBuffer && typeof arrayBuffer.byteLength === 'number' && arrayBuffer.byteLength > 1024) {
            state.encodedChunks++;
            state.encodedBytes += arrayBuffer.byteLength;
            notify('chunk');
          }
        } catch (ignored) {}
        return original.apply(this, arguments);
      };
      state.patched.add(context);
      state.contexts.add(context);
      return true;
    } catch (ignored) { return false; }
  }

  function patchConstructor() {
    if (state.originalContext) return;
    var Original = window.AudioContext || window.webkitAudioContext;
    if (!Original) return;
    try {
      function CapturingAudioContext() {
        var instance = Reflect.construct(Original, arguments, Original);
        patchContext(instance);
        return instance;
      }
      Object.setPrototypeOf(CapturingAudioContext, Original);
      CapturingAudioContext.prototype = Original.prototype;
      window.AudioContext = CapturingAudioContext;
      window.webkitAudioContext = CapturingAudioContext;
      state.originalContext = Original;
    } catch (ignored) {}
  }

  function patchExisting() {
    try {
      var contexts = window.audioSources && window.audioSources.audioContexts;
      if (contexts && typeof contexts.forEach === 'function') {
        contexts.forEach(function (context) { patchContext(context); });
      }
    } catch (ignored) {}
  }

  function cancelEnd() {
    if (state.endTimer) clearTimeout(state.endTimer);
    state.endTimer = null;
  }

  function button() { return document.getElementById('textSpeaker'); }

  function observeButton() {
    var el = button();
    if (!el) return;
    if (!state.hookedButton) {
      state.hookedButton = true;
      el.addEventListener('click', function () {
        if (el.classList.contains('state-playing')) {
          state.manuallyStopped = true;
          state.pausedByUser = false;
        } else {
          state.manuallyStopped = false;
          state.pausedByUser = false;
          state.encodedChunks = 0;
          state.encodedBytes = 0;
        }
      }, true);
      var pauseButton = document.getElementById('speakerPause');
      if (pauseButton) pauseButton.addEventListener('click', function () {
        state.pausedByUser = !state.pausedByUser;
        notify(state.pausedByUser ? 'paused' : 'resumed');
      }, true);
    }
    notify('ready', {buttonDisabled: el.classList.contains('state-disabled')});
  }

  function poll() {
    patchExisting();
    if (!state.hookedButton) observeButton();
    var el = button();
    if (!el) return;
    var nowPlaying = el.classList.contains('state-playing');
    if (nowPlaying) {
      cancelEnd();
      if (!state.playing) {
        state.started = true;
        state.playing = true;
        state.manuallyStopped = false;
        notify('started');
      }
    } else if (state.playing) {
      state.playing = false;
      if (!state.manuallyStopped && !state.pausedByUser && state.started) {
        // Debounce transient UI changes and network buffering, unlike a hard
        // 3-second idle timeout based solely on incoming MPEG chunks.
        cancelEnd();
        state.endTimer = setTimeout(function () {
          state.endTimer = null;
          var current = button();
          if (current && !current.classList.contains('state-playing') &&
              !state.manuallyStopped && !state.pausedByUser && state.started) {
            state.started = false;
            notify('finished');
          }
        }, 850);
      }
    }
  }

  state.setText = function (text) {
    try {
      var area = document.getElementById('textarea');
      if (!area || !window.app) return false;
      var previous = area.value;
      // Keep MRT's internal model synchronized: setting the textarea alone
      // is not sufficient with recent builds of text.modern.min.js.
      if (typeof window.app.setText === 'function') {
        window.app.setText(text, {sender: 'chitalka-rsvp', caret: text.length, oldValue: previous});
      }
      var descriptor = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value');
      if (descriptor && descriptor.set) descriptor.set.call(area, text);
      else area.value = text;
      area.dispatchEvent(new Event('input', {bubbles: true}));
      area.dispatchEvent(new Event('change', {bubbles: true}));
      state.started = false;
      state.playing = false;
      state.pausedByUser = false;
      state.manuallyStopped = false;
      state.encodedChunks = 0;
      state.encodedBytes = 0;
      cancelEnd();
      return true;
    } catch (err) { notify('error', {detail: String(err)}); return false; }
  };

  // Hide the hosted page without breaking its real hit-testable transport.
  // All visible reading text and selectors live in native Android views.
  state.setNativeUiHidden = function (hidden) {
    try {
      var id = 'rsvp-headless-mrt-css';
      var style = document.getElementById(id);
      if (!style) {
        style = document.createElement('style');
        style.id = id;
        style.textContent = [
          'html.rsvp-headless-mrt #textSpeaker {position:fixed!important;left:12px!important;top:12px!important;width:65px!important;height:56px!important;min-width:65px!important;min-height:56px!important;transform:none!important;margin:0!important;z-index:2147483647!important;pointer-events:auto!important;display:block!important;}',
          'html.rsvp-headless-mrt #textarea {caret-color:transparent!important;}'
        ].join('\n');
        document.head.appendChild(style);
      }
      document.documentElement.classList.toggle('rsvp-headless-mrt', !!hidden);
      document.body.style.opacity = hidden ? '0.001' : '';
      document.body.style.overflow = hidden ? 'hidden' : '';
      var area = document.getElementById('textarea');
      if (area) {
        area.readOnly = true;
        area.tabIndex = -1;
        try { area.blur(); } catch (ignored) {}
      }
      if (document.activeElement && document.activeElement !== document.body) {
        try { document.activeElement.blur(); } catch (ignored) {}
      }
      return true;
    } catch (err) { notify('error', {detail:'Headless UI: ' + String(err)}); return false; }
  };

  // Native Android sends MotionEvent touches to #textSpeaker. Calling
  // HTMLElement.click() here may yield an untrusted event rejected by MRT.
  state.prepareStart = function () {
    var el = button();
    if (!el || el.classList.contains('state-disabled')) return 0;
    if (el.classList.contains('state-playing')) return 2;
    state.pausedByUser = false;
    state.manuallyStopped = false;
    cancelEnd();
    return 1;
  };

  state.prepareStop = function () {
    state.manuallyStopped = true;
    state.pausedByUser = false;
    state.started = false;
    cancelEnd();
    var el = button();
    return el && el.classList.contains('state-playing') ? 1 : 0;
  };

  state.forceSilence = function () {
    // Only a fallback when Android WebView rejects an injected touchscreen tap.
    state.manuallyStopped = true;
    state.pausedByUser = false;
    state.started = false;
    cancelEnd();
    patchExisting();
    state.contexts.forEach(function (ctx) {
      try { if (ctx.state === 'running') ctx.suspend(); } catch (ignored) {}
    });
    return true;
  };

  state.startPlayback = function () {
    var el = button();
    if (!el || el.classList.contains('state-disabled')) return false;
    if (el.classList.contains('state-playing')) return true;
    state.pausedByUser = false;
    state.manuallyStopped = false;
    // WebView setMediaPlaybackRequiresUserGesture(false) is enabled. If MRT
    // itself insists on a trusted physical click, the visible WebView button
    // remains accessible to the user.
    el.click();
    return true;
  };

  state.togglePause = function () {
    var el = document.getElementById('speakerPause');
    if (!el) return false;
    el.click();
    return true;
  };

  state.stopPlayback = function () {
    state.manuallyStopped = true;
    state.started = false;
    state.pausedByUser = false;
    cancelEnd();
    var el = button();
    if (el && el.classList.contains('state-playing')) el.click();
    return true;
  };

  state.isPlaying = function () {
    var el = button();
    return !!el && el.classList.contains('state-playing');
  };

  window.__rsvpMrt = state;
  patchConstructor();
  setInterval(poll, 280);
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', observeButton, {once: true});
  }
})();
