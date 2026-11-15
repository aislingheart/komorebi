// Embedded React + @raycast/api + @vicinae/api + @raycast/utils runtime for QuickJS
(function (global) {
  "use strict";

  if (typeof global.global === "undefined") {
    global.global = global;
  }
  if (typeof global.globalThis === "undefined") {
    global.globalThis = global;
  }

  // ---------------------------------------------------------------------------
  // Error formatting & Native diagnostic reporting
  // ---------------------------------------------------------------------------
  function formatError(err) {
    if (!err) return "Unknown error";
    if (typeof err === "string") return err;
    const msg = err.message ? String(err.message) : String(err);
    const stack = err.stack ? String(err.stack) : "";
    if (stack && stack.indexOf(msg) === -1) {
      return msg + "\n" + stack;
    }
    return stack || msg;
  }

  function reportRuntimeError(err, phase, fatal) {
    const message = err && err.message ? String(err.message) : String(err || "Unknown error");
    const stack = err && err.stack ? String(err.stack) : "";
    const label = phase ? "[" + phase + "] " + message : message;
    try {
      console.error(label + (stack ? "\n" + stack : ""));
    } catch (_) {}
    if (typeof global.__hostCallSync === "function") {
      try {
        hostCallSync("runtime.reportError", {
          phase: phase || "runtime",
          message: message,
          stack: stack,
          fatal: Boolean(fatal),
        });
      } catch (_) {}
    }
  }

  // ---------------------------------------------------------------------------
  // Polyfills: console, timers, TextEncoder/TextDecoder, Buffer, URL, WebCrypto, Fetch
  // ---------------------------------------------------------------------------
  if (typeof global.console === "undefined") {
    global.console = {};
  }
  const _origLog = global.console.log ? global.console.log.bind(global.console) : function () {};
  function formatArgs(args) {
    return Array.prototype.slice.call(args).map(function (a) {
      if (typeof a === "string") return a;
      if (a instanceof Error) return formatError(a);
      try { return JSON.stringify(a); } catch (_) { return String(a); }
    }).join(" ");
  }
  global.console.log = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("LOG", formatArgs(arguments)); else _origLog.apply(null, arguments); };
  global.console.info = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("INFO", formatArgs(arguments)); else _origLog.apply(null, arguments); };
  global.console.warn = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("WARN", formatArgs(arguments)); else _origLog.apply(null, arguments); };
  global.console.error = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("ERROR", formatArgs(arguments)); else _origLog.apply(null, arguments); };
  global.console.debug = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("DEBUG", formatArgs(arguments)); else _origLog.apply(null, arguments); };

  if (typeof global.queueMicrotask !== "function") {
    global.queueMicrotask = function (fn) {
      Promise.resolve().then(fn);
    };
  }

  if (typeof global.performance === "undefined") {
    const _startTime = Date.now();
    global.performance = {
      now: function () { return Date.now() - _startTime; },
      timeOrigin: _startTime,
    };
  }

  let _timerSeq = 1;
  const _activeTimers = new Set();
  const _activeIntervals = new Set();
  if (typeof global.setTimeout !== "function") {
    global.setTimeout = function (fn, ms) {
      const id = _timerSeq++;
      const args = Array.prototype.slice.call(arguments, 2);
      _activeTimers.add(id);
      if (typeof global.__scheduleTimeout === "function") {
        global.__scheduleTimeout(id, Math.max(0, Number(ms) || 0)).then(function () {
          if (_activeTimers.delete(id)) {
            try { fn.apply(null, args); } catch (e) { reportRuntimeError(e, "setTimeout", false); }
          }
        });
      } else {
        Promise.resolve().then(function () {
          if (_activeTimers.delete(id)) {
            try { fn.apply(null, args); } catch (e) { reportRuntimeError(e, "setTimeout", false); }
          }
        });
      }
      return id;
    };
    global.clearTimeout = function (id) { _activeTimers.delete(id); };
  }
  if (typeof global.setInterval !== "function") {
    global.setInterval = function (fn, ms) {
      const id = _timerSeq++;
      const args = Array.prototype.slice.call(arguments, 2);
      const delay = Math.max(16, Number(ms) || 16);
      _activeIntervals.add(id);
      function tick() {
        if (!_activeIntervals.has(id)) return;
        global.setTimeout(function () {
          if (!_activeIntervals.has(id)) return;
          try { fn.apply(null, args); } catch (e) { reportRuntimeError(e, "setInterval", false); }
          tick();
        }, delay);
      }
      tick();
      return id;
    };
    global.clearInterval = function (id) { _activeIntervals.delete(id); };
  }
  if (typeof global.setImmediate !== "function") {
    global.setImmediate = function (fn) {
      const args = Array.prototype.slice.call(arguments, 1);
      return global.setTimeout(function () { fn.apply(null, args); }, 0);
    };
    global.clearImmediate = function (id) { global.clearTimeout(id); };
  }

  if (typeof global.atob !== "function") {
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    global.btoa = function (input) {
      const str = String(input);
      let out = "";
      for (let i = 0; i < str.length; i += 3) {
        const a = str.charCodeAt(i) & 255;
        const b = i + 1 < str.length ? (str.charCodeAt(i + 1) & 255) : 0;
        const c = i + 2 < str.length ? (str.charCodeAt(i + 2) & 255) : 0;
        const triple = (a << 16) | (b << 8) | c;
        out += chars[(triple >> 18) & 63];
        out += chars[(triple >> 12) & 63];
        out += i + 1 < str.length ? chars[(triple >> 6) & 63] : "=";
        out += i + 2 < str.length ? chars[triple & 63] : "=";
      }
      return out;
    };
    global.atob = function (input) {
      const str = String(input).replace(/[\r\n\s]/g, "").replace(/=+$/, "");
      let out = "";
      for (let i = 0; i < str.length; i += 4) {
        const e1 = chars.indexOf(str[i]);
        const e2 = chars.indexOf(str[i + 1]);
        const e3 = chars.indexOf(str[i + 2]);
        const e4 = chars.indexOf(str[i + 3]);
        const n = (e1 << 18) | (e2 << 12) | ((e3 >= 0 ? e3 : 0) << 6) | (e4 >= 0 ? e4 : 0);
        out += String.fromCharCode((n >> 16) & 255);
        if (e3 >= 0 && str[i + 2] !== undefined) out += String.fromCharCode((n >> 8) & 255);
        if (e4 >= 0 && str[i + 3] !== undefined) out += String.fromCharCode(n & 255);
      }
      return out;
    };
  }

  if (typeof global.TextEncoder === "undefined") {
    global.TextEncoder = function TextEncoder() {
      this.encoding = "utf-8";
    };
    global.TextEncoder.prototype.encode = function (str) {
      const utf8 = unescape(encodeURIComponent(String(str == null ? "" : str)));
      const arr = new Uint8Array(utf8.length);
      for (let i = 0; i < utf8.length; i++) arr[i] = utf8.charCodeAt(i);
      return arr;
    };
    global.TextEncoder.prototype.encodeInto = function (str, dest) {
      const encoded = this.encode(str);
      const len = Math.min(encoded.length, dest.length);
      for (let i = 0; i < len; i++) dest[i] = encoded[i];
      return { read: String(str).length, written: len };
    };
  }
  if (typeof global.TextDecoder === "undefined") {
    global.TextDecoder = function TextDecoder(label, opts) {
      this.encoding = label ? String(label).toLowerCase() : "utf-8";
      this.fatal = Boolean(opts && opts.fatal);
      this.ignoreBOM = Boolean(opts && opts.ignoreBOM);
    };
    global.TextDecoder.prototype.decode = function (bytes) {
      if (!bytes) return "";
      const arr = bytes instanceof Uint8Array
        ? bytes
        : (bytes instanceof ArrayBuffer ? new Uint8Array(bytes) : new Uint8Array(bytes.buffer || bytes));
      let s = "";
      for (let i = 0; i < arr.length; i++) s += String.fromCharCode(arr[i]);
      if (this.encoding === "latin1" || this.encoding === "iso-8859-1" || this.encoding === "ascii") {
        return s;
      }
      try {
        return decodeURIComponent(escape(s));
      } catch (e) {
        if (this.fatal) throw new TypeError("The encoded data was not valid.");
        return s;
      }
    };
  }

  if (typeof global.Buffer === "undefined") {
    function _wrapBuffer(arr) {
      const out = arr instanceof Uint8Array ? arr : new Uint8Array(arr || 0);
      out.toString = _bufToString;
      out.equals = _bufEquals;
      out.toJSON = _bufToJSON;
      out.write = _bufWrite;
      out.copy = _bufCopy;
      return out;
    }
    function _Buffer(arg, enc) {
      return _Buffer.from(arg, enc);
    }
    function _bufToString(enc, start, end) {
      const slice = (start !== undefined || end !== undefined) ? this.subarray(start || 0, end) : this;
      const e = (enc || "utf8").toLowerCase();
      if (e === "base64" || e === "base64url") {
        let bin = "";
        for (let i = 0; i < slice.length; i++) bin += String.fromCharCode(slice[i]);
        let b64 = global.btoa(bin);
        if (e === "base64url") b64 = b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
        return b64;
      }
      if (e === "hex") {
        let hex = "";
        for (let i = 0; i < slice.length; i++) hex += ("0" + slice[i].toString(16)).slice(-2);
        return hex;
      }
      if (e === "latin1" || e === "binary" || e === "ascii") {
        let str = "";
        for (let i = 0; i < slice.length; i++) str += String.fromCharCode(e === "ascii" ? (slice[i] & 0x7f) : slice[i]);
        return str;
      }
      return new global.TextDecoder().decode(slice);
    }
    function _bufEquals(other) {
      if (!other || this.length !== other.length) return false;
      for (let i = 0; i < this.length; i++) {
        if (this[i] !== other[i]) return false;
      }
      return true;
    }
    function _bufToJSON() {
      return { type: "Buffer", data: Array.prototype.slice.call(this) };
    }
    function _bufWrite(str, offset, length, enc) {
      const off = Number(offset) || 0;
      const src = _Buffer.from(String(str), enc);
      const maxLen = Math.min(src.length, length !== undefined ? Number(length) : this.length - off, this.length - off);
      for (let i = 0; i < maxLen; i++) this[off + i] = src[i];
      return maxLen;
    }
    function _bufCopy(target, targetStart, sourceStart, sourceEnd) {
      const tStart = targetStart || 0;
      const sStart = sourceStart || 0;
      const sEnd = sourceEnd !== undefined ? sourceEnd : this.length;
      let count = 0;
      for (let i = sStart; i < sEnd && (tStart + count) < target.length; i++) {
        target[tStart + count] = this[i];
        count++;
      }
      return count;
    }
    _Buffer.from = function (input, encOrOffset, length) {
      if (typeof input === "string") {
        const enc = (encOrOffset || "utf8").toLowerCase();
        if (enc === "base64" || enc === "base64url") {
          let b64 = input;
          if (enc === "base64url") {
            b64 = b64.replace(/-/g, "+").replace(/_/g, "/");
            while (b64.length % 4) b64 += "=";
          }
          const bin = global.atob(b64);
          const out = new Uint8Array(bin.length);
          for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
          return _wrapBuffer(out);
        }
        if (enc === "hex") {
          const clean = input.length % 2 === 0 ? input : input.slice(0, -1);
          const out = new Uint8Array(clean.length / 2);
          for (let i = 0; i < out.length; i++) out[i] = parseInt(clean.substr(i * 2, 2), 16) || 0;
          return _wrapBuffer(out);
        }
        if (enc === "latin1" || enc === "binary" || enc === "ascii") {
          const out = new Uint8Array(input.length);
          for (let i = 0; i < input.length; i++) out[i] = input.charCodeAt(i) & 0xff;
          return _wrapBuffer(out);
        }
        return _wrapBuffer(new global.TextEncoder().encode(input));
      }
      if (input instanceof ArrayBuffer) {
        return _wrapBuffer(new Uint8Array(input, encOrOffset || 0, length));
      }
      if (ArrayBuffer.isView(input)) {
        return _wrapBuffer(new Uint8Array(input.buffer, input.byteOffset, input.byteLength));
      }
      return _wrapBuffer(new Uint8Array(input || 0));
    };
    _Buffer.alloc = function (n, fill, enc) {
      const out = _wrapBuffer(new Uint8Array(Math.max(0, Number(n) || 0)));
      if (fill !== undefined) {
        if (typeof fill === "number") {
          out.fill(fill & 0xff);
        } else {
          const f = _Buffer.from(String(fill), enc);
          if (f.length > 0) {
            for (let i = 0; i < out.length; i++) out[i] = f[i % f.length];
          }
        }
      }
      return out;
    };
    _Buffer.allocUnsafe = function (n) { return _Buffer.alloc(n); };
    _Buffer.isBuffer = function (x) { return Boolean(x && x instanceof Uint8Array); };
    _Buffer.isEncoding = function (enc) {
      return ["utf8", "utf-8", "base64", "base64url", "hex", "ascii", "latin1", "binary"].indexOf(String(enc || "").toLowerCase()) !== -1;
    };
    _Buffer.byteLength = function (str, enc) {
      if (str instanceof Uint8Array || str instanceof ArrayBuffer || ArrayBuffer.isView(str)) return str.byteLength;
      return _Buffer.from(String(str), enc).length;
    };
    _Buffer.concat = function (list, totalLength) {
      const arr = Array.isArray(list) ? list : [];
      let len = totalLength;
      if (len === undefined) {
        len = 0;
        for (let i = 0; i < arr.length; i++) len += arr[i].length;
      }
      const out = _Buffer.alloc(len);
      let offset = 0;
      for (let i = 0; i < arr.length && offset < len; i++) {
        const buf = arr[i];
        const copyLen = Math.min(buf.length, len - offset);
        out.set(buf.subarray ? buf.subarray(0, copyLen) : buf.slice(0, copyLen), offset);
        offset += copyLen;
      }
      return out;
    };
    _Buffer.compare = function (a, b) {
      const min = Math.min(a.length, b.length);
      for (let i = 0; i < min; i++) {
        if (a[i] < b[i]) return -1;
        if (a[i] > b[i]) return 1;
      }
      return a.length < b.length ? -1 : (a.length > b.length ? 1 : 0);
    };
    global.Buffer = _Buffer;
  }

  if (typeof global.URLSearchParams === "undefined") {
    global.URLSearchParams = function URLSearchParams(init) {
      this._entries = [];
      if (typeof init === "string") {
        const clean = init.startsWith("?") ? init.slice(1) : init;
        if (clean) {
          for (const part of clean.split("&")) {
            if (!part) continue;
            const eq = part.indexOf("=");
            const k = decodeURIComponent((eq >= 0 ? part.slice(0, eq) : part).replace(/\+/g, " "));
            const v = eq >= 0 ? decodeURIComponent(part.slice(eq + 1).replace(/\+/g, " ")) : "";
            this._entries.push([k, v]);
          }
        }
      } else if (Array.isArray(init)) {
        for (const pair of init) {
          if (Array.isArray(pair) && pair.length >= 2) {
            this._entries.push([String(pair[0]), String(pair[1])]);
          }
        }
      } else if (init && typeof init === "object") {
        if (typeof init.forEach === "function" && init._entries) {
          for (const e of init._entries) this._entries.push([e[0], e[1]]);
        } else {
          for (const k of Object.keys(init)) {
            this._entries.push([String(k), String(init[k])]);
          }
        }
      }
    };
    global.URLSearchParams.prototype.append = function (k, v) { this._entries.push([String(k), String(v)]); };
    global.URLSearchParams.prototype.set = function (k, v) {
      const ks = String(k);
      this._entries = this._entries.filter((e) => e[0] !== ks);
      this._entries.push([ks, String(v)]);
    };
    global.URLSearchParams.prototype.get = function (k) {
      const found = this._entries.find((e) => e[0] === String(k));
      return found ? found[1] : null;
    };
    global.URLSearchParams.prototype.getAll = function (k) {
      const ks = String(k);
      return this._entries.filter((e) => e[0] === ks).map((e) => e[1]);
    };
    global.URLSearchParams.prototype.has = function (k) { return this._entries.some((e) => e[0] === String(k)); };
    global.URLSearchParams.prototype.delete = function (k) { this._entries = this._entries.filter((e) => e[0] !== String(k)); };
    global.URLSearchParams.prototype.sort = function () {
      this._entries.sort((a, b) => (a[0] < b[0] ? -1 : (a[0] > b[0] ? 1 : 0)));
    };
    global.URLSearchParams.prototype.toString = function () {
      return this._entries.map(([k, v]) => encodeURIComponent(k) + "=" + encodeURIComponent(v)).join("&");
    };
    global.URLSearchParams.prototype.entries = function () { return this._entries[Symbol.iterator](); };
    global.URLSearchParams.prototype.keys = function () { return this._entries.map((e) => e[0])[Symbol.iterator](); };
    global.URLSearchParams.prototype.values = function () { return this._entries.map((e) => e[1])[Symbol.iterator](); };
    global.URLSearchParams.prototype.forEach = function (cb, thisArg) {
      for (const [k, v] of this._entries) cb.call(thisArg, v, k, this);
    };
    global.URLSearchParams.prototype[Symbol.iterator] = global.URLSearchParams.prototype.entries;
    Object.defineProperty(global.URLSearchParams.prototype, "size", {
      get: function () { return this._entries.length; },
    });
  }

  if (typeof global.URL === "undefined") {
    function parseUrlParts(input, base) {
      let raw = String(input);
      if (base && !/^[a-zA-Z][a-zA-Z\d+\-.]*:/.test(raw)) {
        const b = String(base);
        if (raw.startsWith("//")) {
          const proto = b.match(/^([a-zA-Z][a-zA-Z\d+\-.]*:)/);
          raw = (proto ? proto[1] : "https:") + raw;
        } else if (raw.startsWith("/")) {
          const m = b.match(/^([a-zA-Z][a-zA-Z\d+\-.]*:\/\/[^/]+)/);
          raw = (m ? m[1] : "") + raw;
        } else if (raw.startsWith("?")) {
          raw = b.split("?")[0].split("#")[0] + raw;
        } else if (raw.startsWith("#")) {
          raw = b.split("#")[0] + raw;
        } else {
          raw = b.split("?")[0].split("#")[0].replace(/[^/]*$/, "") + raw;
        }
      }
      const m = raw.match(/^([a-zA-Z][a-zA-Z\d+\-.]*:)\/\/([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/);
      if (!m) {
        const schemeOnly = raw.match(/^([a-zA-Z][a-zA-Z\d+\-.]*:)([^?#]*)(\?[^#]*)?(#.*)?$/);
        if (!schemeOnly) throw new TypeError("Invalid URL: " + input);
        return {
          protocol: schemeOnly[1].toLowerCase(),
          host: "",
          hostname: "",
          port: "",
          pathname: schemeOnly[2] || "",
          search: schemeOnly[3] || "",
          hash: schemeOnly[4] || "",
          origin: "null",
        };
      }
      const host = m[2] || "";
      const atIdx = host.lastIndexOf("@");
      const cleanHost = atIdx >= 0 ? host.slice(atIdx + 1) : host;
      const hp = cleanHost.split(":");
      const protocol = m[1].toLowerCase();
      return {
        protocol: protocol,
        host: cleanHost,
        hostname: hp[0] || "",
        port: hp[1] || "",
        pathname: m[3] || "/",
        search: m[4] || "",
        hash: m[5] || "",
        origin: protocol + "//" + cleanHost,
      };
    }

    global.URL = function URL(input, base) {
      const parts = parseUrlParts(input, base);
      this.protocol = parts.protocol;
      this.host = parts.host;
      this.hostname = parts.hostname;
      this.port = parts.port;
      this.pathname = parts.pathname;
      this.search = parts.search;
      this.hash = parts.hash;
      this.origin = parts.origin;
      this.searchParams = new global.URLSearchParams(this.search);
    };
    global.URL.prototype.toString = function () {
      const qs = this.searchParams.toString();
      const s = qs ? "?" + qs : "";
      const authority = this.host ? "//" + this.host : "";
      return this.protocol + authority + this.pathname + s + this.hash;
    };
    global.URL.prototype.toJSON = global.URL.prototype.toString;
    Object.defineProperty(global.URL.prototype, "href", {
      get: function () { return this.toString(); },
      set: function (val) {
        const parts = parseUrlParts(val);
        this.protocol = parts.protocol;
        this.host = parts.host;
        this.hostname = parts.hostname;
        this.port = parts.port;
        this.pathname = parts.pathname;
        this.search = parts.search;
        this.hash = parts.hash;
        this.origin = parts.origin;
        this.searchParams = new global.URLSearchParams(this.search);
      },
    });
    global.URL.canParse = function (input, base) {
      try { new global.URL(input, base); return true; } catch (_) { return false; }
    };
  }

  if (typeof global.AbortController === "undefined") {
    function AbortSignal() {
      this.aborted = false;
      this.reason = undefined;
      this._listeners = new Set();
    }
    AbortSignal.prototype.addEventListener = function (evt, fn) {
      if (evt === "abort" && typeof fn === "function") this._listeners.add(fn);
    };
    AbortSignal.prototype.removeEventListener = function (evt, fn) {
      if (evt === "abort") this._listeners.delete(fn);
    };
    AbortSignal.prototype.throwIfAborted = function () {
      if (this.aborted) throw this.reason || new Error("Aborted");
    };
    AbortSignal.abort = function (reason) {
      const s = new AbortSignal();
      s.aborted = true;
      s.reason = reason !== undefined ? reason : new Error("Aborted");
      return s;
    };
    AbortSignal.timeout = function (ms) {
      const s = new AbortSignal();
      global.setTimeout(function () {
        s.aborted = true;
        s.reason = new Error("TimeoutError");
        for (const fn of s._listeners) { try { fn({ type: "abort" }); } catch (_) {} }
      }, ms);
      return s;
    };
    global.AbortSignal = AbortSignal;
    global.AbortController = function AbortController() {
      this.signal = new AbortSignal();
      this.abort = (reason) => {
        if (this.signal.aborted) return;
        this.signal.aborted = true;
        this.signal.reason = reason !== undefined ? reason : new Error("AbortError");
        if (typeof this.signal.onabort === "function") {
          try { this.signal.onabort({ type: "abort" }); } catch (_) {}
        }
        for (const fn of this.signal._listeners) {
          try { fn({ type: "abort" }); } catch (_) {}
        }
      };
    };
  }

  if (typeof global.Headers === "undefined") {
    global.Headers = function Headers(init) {
      this._map = new Map();
      if (init) {
        if (typeof init.forEach === "function" && !(init instanceof Array)) {
          init.forEach((v, k) => this.append(k, v));
        } else if (Array.isArray(init)) {
          for (const pair of init) {
            if (Array.isArray(pair) && pair.length >= 2) this.append(pair[0], pair[1]);
          }
        } else if (typeof init === "object") {
          for (const k of Object.keys(init)) this.set(k, init[k]);
        }
      }
    };
    global.Headers.prototype.get = function (k) {
      const entry = this._map.get(String(k).toLowerCase());
      return entry ? entry[1] : null;
    };
    global.Headers.prototype.set = function (k, v) {
      this._map.set(String(k).toLowerCase(), [String(k), String(v)]);
    };
    global.Headers.prototype.append = function (k, v) {
      const lk = String(k).toLowerCase();
      const prev = this._map.get(lk);
      this._map.set(lk, [String(k), prev ? prev[1] + ", " + String(v) : String(v)]);
    };
    global.Headers.prototype.has = function (k) { return this._map.has(String(k).toLowerCase()); };
    global.Headers.prototype.delete = function (k) { this._map.delete(String(k).toLowerCase()); };
    global.Headers.prototype.forEach = function (cb, thisArg) {
      for (const [, pair] of this._map.entries()) cb.call(thisArg, pair[1], pair[0], this);
    };
    global.Headers.prototype.entries = function () {
      const arr = [];
      for (const [lk, pair] of this._map.entries()) arr.push([lk, pair[1]]);
      return arr[Symbol.iterator]();
    };
    global.Headers.prototype.keys = function () { return this._map.keys(); };
    global.Headers.prototype.values = function () {
      const arr = [];
      for (const [, pair] of this._map.entries()) arr.push(pair[1]);
      return arr[Symbol.iterator]();
    };
    global.Headers.prototype[Symbol.iterator] = global.Headers.prototype.entries;
  }

  if (typeof global.Blob === "undefined") {
    global.Blob = function Blob(parts, options) {
      const chunks = Array.isArray(parts) ? parts : [];
      const bufList = chunks.map(function (p) {
        if (p instanceof Uint8Array) return p;
        if (p instanceof ArrayBuffer) return new Uint8Array(p);
        if (p && p._bytes instanceof Uint8Array) return p._bytes;
        return new global.TextEncoder().encode(String(p));
      });
      this._bytes = global.Buffer.concat(bufList);
      this.size = this._bytes.length;
      this.type = (options && options.type) ? String(options.type) : "";
    };
    global.Blob.prototype.text = function () { return Promise.resolve(new global.TextDecoder().decode(this._bytes)); };
    global.Blob.prototype.arrayBuffer = function () {
      const copy = new Uint8Array(this._bytes.length);
      copy.set(this._bytes);
      return Promise.resolve(copy.buffer);
    };
    global.Blob.prototype.bytes = function () { return Promise.resolve(new Uint8Array(this._bytes)); };
  }

  if (typeof global.FormData === "undefined") {
    global.FormData = function FormData() { this._entries = []; };
    global.FormData.prototype.append = function (k, v) { this._entries.push([String(k), v]); };
    global.FormData.prototype.set = function (k, v) {
      const ks = String(k);
      this._entries = this._entries.filter((e) => e[0] !== ks);
      this._entries.push([ks, v]);
    };
    global.FormData.prototype.get = function (k) {
      const found = this._entries.find((e) => e[0] === String(k));
      return found ? found[1] : null;
    };
    global.FormData.prototype.getAll = function (k) {
      return this._entries.filter((e) => e[0] === String(k)).map((e) => e[1]);
    };
    global.FormData.prototype.has = function (k) { return this._entries.some((e) => e[0] === String(k)); };
    global.FormData.prototype.delete = function (k) { this._entries = this._entries.filter((e) => e[0] !== String(k)); };
    global.FormData.prototype.entries = function () { return this._entries[Symbol.iterator](); };
    global.FormData.prototype.keys = function () { return this._entries.map((e) => e[0])[Symbol.iterator](); };
    global.FormData.prototype.values = function () { return this._entries.map((e) => e[1])[Symbol.iterator](); };
    global.FormData.prototype[Symbol.iterator] = global.FormData.prototype.entries;
  }

  if (typeof global.Request === "undefined") {
    global.Request = function Request(input, init) {
      const opts = init || {};
      if (input && typeof input === "object" && input.url) {
        this.url = input.url;
        this.method = (opts.method || input.method || "GET").toUpperCase();
        this.headers = new global.Headers(opts.headers || input.headers);
        this.body = opts.body !== undefined ? opts.body : input.body;
        this.signal = opts.signal || input.signal;
      } else {
        this.url = String(input);
        this.method = (opts.method || "GET").toUpperCase();
        this.headers = new global.Headers(opts.headers);
        this.body = opts.body;
        this.signal = opts.signal;
      }
    };
  }

  if (typeof global.Response === "undefined") {
    function makeResponse(rawBytes, textBody, init) {
      const opts = init || {};
      const status = opts.status !== undefined ? opts.status : 200;
      const headers = opts.headers instanceof global.Headers ? opts.headers : new global.Headers(opts.headers || {});
      const resp = {
        ok: status >= 200 && status < 300,
        status: status,
        statusText: opts.statusText || "",
        url: opts.url || "",
        redirected: false,
        type: "basic",
        headers: headers,
        bodyUsed: false,
        text: function () {
          resp.bodyUsed = true;
          if (textBody !== null && textBody !== undefined) return Promise.resolve(textBody);
          return Promise.resolve(new global.TextDecoder().decode(rawBytes || new Uint8Array(0)));
        },
        json: function () {
          return resp.text().then(function (t) { return JSON.parse(t); });
        },
        arrayBuffer: function () {
          resp.bodyUsed = true;
          const b = rawBytes || new global.TextEncoder().encode(textBody || "");
          const copy = new Uint8Array(b.length);
          copy.set(b);
          return Promise.resolve(copy.buffer);
        },
        bytes: function () {
          resp.bodyUsed = true;
          return Promise.resolve(new Uint8Array(rawBytes || new global.TextEncoder().encode(textBody || "")));
        },
        blob: function () {
          resp.bodyUsed = true;
          return Promise.resolve(new global.Blob([rawBytes || textBody || ""], { type: headers.get("content-type") || "" }));
        },
        clone: function () {
          return makeResponse(rawBytes, textBody, { status: status, statusText: opts.statusText, url: opts.url, headers: headers });
        },
      };
      return resp;
    }
    global.Response = function Response(body, init) {
      if (body instanceof Uint8Array || body instanceof ArrayBuffer) {
        return makeResponse(body instanceof Uint8Array ? body : new Uint8Array(body), null, init);
      }
      return makeResponse(null, body != null ? String(body) : "", init);
    };
    global.Response.json = function (data, init) {
      const opts = Object.assign({}, init || {});
      const h = new global.Headers(opts.headers || {});
      if (!h.has("content-type")) h.set("content-type", "application/json");
      opts.headers = h;
      return new global.Response(JSON.stringify(data), opts);
    };
    global.Response._fromNative = makeResponse;
  }

  if (typeof global.fetch !== "function") {
    global.fetch = function (input, init) {
      const isReq = input && typeof input === "object" && input.url !== undefined;
      const url = isReq ? input.url : String(input);
      const opts = init || {};
      const signal = opts.signal || (isReq ? input.signal : null);
      if (signal && signal.aborted) {
        return Promise.reject(signal.reason || new Error("The operation was aborted."));
      }

      const method = (opts.method || (isReq ? input.method : "GET") || "GET").toUpperCase();
      const headers = {};
      const srcHeaders = opts.headers || (isReq ? input.headers : null);
      if (srcHeaders) {
        if (typeof srcHeaders.forEach === "function") {
          srcHeaders.forEach((v, k) => { headers[k] = v; });
        } else if (Array.isArray(srcHeaders)) {
          for (const pair of srcHeaders) {
            if (Array.isArray(pair) && pair.length >= 2) headers[pair[0]] = pair[1];
          }
        } else {
          Object.assign(headers, srcHeaders);
        }
      }

      const rawBody = opts.body !== undefined ? opts.body : (isReq ? input.body : null);
      let body = null;
      let bodyBase64 = null;
      if (rawBody != null) {
        if (rawBody instanceof Uint8Array || ArrayBuffer.isView(rawBody) || rawBody instanceof ArrayBuffer) {
          bodyBase64 = global.Buffer.from(rawBody).toString("base64");
        } else if (typeof global.FormData !== "undefined" && rawBody instanceof global.FormData) {
          const boundary = "----KomorebiForm" + Date.now().toString(16);
          if (!Object.keys(headers).some((k) => k.toLowerCase() === "content-type")) {
            headers["Content-Type"] = "multipart/form-data; boundary=" + boundary;
          }
          const parts = [];
          for (const [k, v] of rawBody.entries()) {
            parts.push("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + k.replace(/"/g, '\\"') + "\"\r\n\r\n" + String(v) + "\r\n");
          }
          parts.push("--" + boundary + "--\r\n");
          body = parts.join("");
        } else {
          body = String(rawBody);
        }
      }

      return global.__nativeFetch(JSON.stringify({ url, method, headers, body, bodyBase64 })).then(function (rawRes) {
        if (signal && signal.aborted) {
          throw signal.reason || new Error("The operation was aborted.");
        }
        const res = typeof rawRes === "string" ? JSON.parse(rawRes) : rawRes;
        if (res.error) throw new Error(res.error);
        const respHeaders = new global.Headers(res.headers || {});
        const rawBytes = res.bodyBase64 ? global.Buffer.from(res.bodyBase64, "base64") : null;
        const textBody = res.body !== undefined && res.body !== null ? String(res.body) : null;
        if (global.Response && global.Response._fromNative) {
          return global.Response._fromNative(rawBytes, textBody, {
            status: res.status,
            statusText: res.statusText || "",
            url: res.url || url,
            headers: respHeaders,
          });
        }
        return {
          ok: res.status >= 200 && res.status < 300,
          status: res.status,
          statusText: res.statusText || "",
          url: res.url || url,
          headers: respHeaders,
          text: function () { return Promise.resolve(textBody || ""); },
          json: function () { return Promise.resolve(JSON.parse(textBody || "")); },
          arrayBuffer: function () { return Promise.resolve(new global.TextEncoder().encode(textBody || "").buffer); },
        };
      });
    };
  }

  if (typeof global.structuredClone !== "function") {
    global.structuredClone = function structuredClone(val) {
      if (val === null || typeof val !== "object") return val;
      if (val instanceof Date) return new Date(val.getTime());
      if (val instanceof RegExp) return new RegExp(val.source, val.flags);
      if (val instanceof Uint8Array) return new Uint8Array(val);
      if (Array.isArray(val)) return val.map(structuredClone);
      if (val instanceof Map) {
        const m = new Map();
        for (const [k, v] of val.entries()) m.set(structuredClone(k), structuredClone(v));
        return m;
      }
      if (val instanceof Set) {
        const s = new Set();
        for (const v of val.values()) s.add(structuredClone(v));
        return s;
      }
      const out = {};
      for (const k of Object.keys(val)) out[k] = structuredClone(val[k]);
      return out;
    };
  }

  // ---------------------------------------------------------------------------
  // Host Bridge & Two-Generation Callback Manager
  // ---------------------------------------------------------------------------
  let _cbSeq = 1;
  let _callbacks = new Map();
  let _prevCallbacks = new Map();
  const _eventListeners = new Map();

  function registerCallback(fn) {
    const id = "cb_" + (_cbSeq++);
    _callbacks.set(id, fn);
    return id;
  }

  function resolveCallback(handlerId) {
    return _callbacks.get(handlerId) || _prevCallbacks.get(handlerId);
  }

  global.__invokeCallback = function (handlerId, argsJson) {
    const fn = resolveCallback(handlerId);
    if (!fn) return null;
    try {
      const args = argsJson ? JSON.parse(argsJson) : [];
      const result = fn.apply(null, args);
      if (result && typeof result.then === "function") {
        result.catch(function (err) {
          reportRuntimeError(err, "async callback", false);
          try {
            showToast({
              style: ToastStyle.Failure,
              title: "Action Failed",
              message: err && err.message ? err.message : String(err),
            });
          } catch (_) {}
        });
        return null;
      }
      return result === undefined ? null : JSON.stringify(result);
    } catch (err) {
      reportRuntimeError(err, "callback", false);
      try {
        showToast({
          style: ToastStyle.Failure,
          title: "Action Failed",
          message: err && err.message ? err.message : String(err),
        });
      } catch (_) {}
      return null;
    }
  };

  global.__dispatchHostEvent = function (eventName, payloadJson) {
    const listeners = _eventListeners.get(eventName);
    if (!listeners) return;
    const payload = payloadJson ? JSON.parse(payloadJson) : {};
    for (const fn of Array.from(listeners)) {
      try { fn(payload); } catch (e) { reportRuntimeError(e, "hostEvent:" + eventName, false); }
    }
  };

  function subscribeEvent(eventName, fn) {
    if (!_eventListeners.has(eventName)) _eventListeners.set(eventName, new Set());
    _eventListeners.get(eventName).add(fn);
    return {
      unsubscribe: function () {
        const set = _eventListeners.get(eventName);
        if (set) set.delete(fn);
      },
    };
  }

  function hostCall(method, params) {
    if (typeof global.__hostCall !== "function") {
      return Promise.resolve(null);
    }
    return global.__hostCall(method, JSON.stringify(params || {})).then(function (raw) {
      if (raw == null || raw === "") return null;
      return typeof raw === "string" ? JSON.parse(raw) : raw;
    });
  }

  function hostCallSync(method, params) {
    if (typeof global.__hostCallSync !== "function") return null;
    const raw = global.__hostCallSync(method, JSON.stringify(params || {}));
    if (raw == null || raw === "") return null;
    return typeof raw === "string" ? JSON.parse(raw) : raw;
  }

  if (typeof global.crypto === "undefined") {
    global.crypto = {
      randomUUID: function () {
        return hostCallSync("crypto.randomUuid", {}) || ("00000000-0000-4000-8000-" + Date.now().toString(16).padStart(12, "0").slice(-12));
      },
      getRandomValues: function (typedArray) {
        if (!typedArray || typeof typedArray.byteLength !== "number") {
          throw new TypeError("Expected TypedArray");
        }
        const total = typedArray.byteLength;
        let offset = 0;
        const dst = new Uint8Array(typedArray.buffer, typedArray.byteOffset, total);
        while (offset < total) {
          const chunk = Math.min(256, total - offset);
          const bytes = hostCallSync("crypto.randomBytes", { length: chunk }) || [];
          for (let i = 0; i < chunk; i++) {
            dst[offset + i] = bytes[i] !== undefined ? (bytes[i] & 0xff) : Math.floor(Math.random() * 256);
          }
          offset += chunk;
        }
        return typedArray;
      },
      subtle: {
        digest: function (algorithm, data) {
          const algName = (typeof algorithm === "string" ? algorithm : (algorithm && algorithm.name) || "SHA-256")
            .toLowerCase().replace(/[^a-z0-9]/g, "");
          const bytes = data instanceof Uint8Array
            ? data
            : (data instanceof ArrayBuffer ? new Uint8Array(data) : new Uint8Array(data && data.buffer ? data.buffer : data));
          const b64 = global.Buffer.from(bytes).toString("base64");
          const outBytes = hostCallSync("crypto.hashBytesBase64", { alg: algName, base64: b64 });
          if (Array.isArray(outBytes)) {
            return Promise.resolve(new Uint8Array(outBytes).buffer);
          }
          return Promise.reject(new Error("crypto.subtle.digest failed for algorithm: " + algName));
        },
      },
    };
  }

  // ---------------------------------------------------------------------------
  // Lightweight React 18/19 + Reconciler Engine
  // ---------------------------------------------------------------------------
  const REACT_ELEMENT_TYPE = Symbol.for("react.transitional.element");
  const REACT_ELEMENT_LEGACY_TYPE = Symbol.for("react.element");
  const FRAGMENT_TYPE = Symbol.for("react.fragment");
  const CONTEXT_TYPE = Symbol.for("react.context");

  let _currentFiber = null;
  let _hookIndex = 0;
  let _idSeq = 1;
  let _rootElement = null;
  let _rootInstanceTree = null;
  let _renderScheduled = false;
  let _isMounted = false;
  const _pendingEffects = [];

  function Component(props, context) {
    this.props = props || {};
    this.context = context;
    this.refs = {};
  }
  Component.prototype.isReactComponent = {};
  Component.prototype.setState = function (partialState, callback) {
    const fiber = this._reactFiber;
    const prev = this.state || {};
    const nextPartial = typeof partialState === "function" ? partialState(prev, this.props) : partialState;
    this.state = Object.assign({}, prev, nextPartial || {});
    if (typeof callback === "function") {
      _pendingEffects.push(callback);
    }
    if (!fiber || _isMounted) scheduleRender();
  };
  Component.prototype.forceUpdate = function (callback) {
    if (typeof callback === "function") _pendingEffects.push(callback);
    if (_isMounted) scheduleRender();
  };

  function PureComponent(props, context) {
    Component.call(this, props, context);
  }
  PureComponent.prototype = Object.create(Component.prototype);
  PureComponent.prototype.constructor = PureComponent;
  PureComponent.prototype.isPureReactComponent = true;

  function createElement(type, props) {
    const outProps = {};
    let key = null;
    if (props != null) {
      if (props.key !== undefined && props.key !== null) key = String(props.key);
      for (const k of Object.keys(props)) {
        if (k !== "key") outProps[k] = props[k];
      }
    }
    const childrenLen = arguments.length - 2;
    if (childrenLen === 1) {
      outProps.children = arguments[2];
    } else if (childrenLen > 1) {
      const childArray = new Array(childrenLen);
      for (let i = 0; i < childrenLen; i++) childArray[i] = arguments[i + 2];
      outProps.children = childArray;
    }
    if (type && type.defaultProps) {
      for (const k of Object.keys(type.defaultProps)) {
        if (outProps[k] === undefined) outProps[k] = type.defaultProps[k];
      }
    }
    return {
      $$typeof: REACT_ELEMENT_TYPE,
      type: type,
      key: key,
      props: outProps,
    };
  }

  function jsx(type, props, key) {
    const outProps = {};
    if (props) {
      for (const k of Object.keys(props)) {
        if (k !== "key") outProps[k] = props[k];
      }
    }
    if (type && type.defaultProps) {
      for (const k of Object.keys(type.defaultProps)) {
        if (outProps[k] === undefined) outProps[k] = type.defaultProps[k];
      }
    }
    const resolvedKey = (key !== undefined && key !== null)
      ? String(key)
      : (props && props.key !== undefined && props.key !== null ? String(props.key) : null);
    return {
      $$typeof: REACT_ELEMENT_TYPE,
      type: type,
      key: resolvedKey,
      props: outProps,
    };
  }

  function isValidElement(obj) {
    return typeof obj === "object" && obj !== null && (
      obj.$$typeof === REACT_ELEMENT_TYPE ||
      obj.$$typeof === REACT_ELEMENT_LEGACY_TYPE ||
      (obj.type !== undefined && obj.props !== undefined)
    );
  }

  function cloneElement(element, config) {
    const newProps = Object.assign({}, element.props, config);
    const childrenLen = arguments.length - 2;
    if (childrenLen === 1) newProps.children = arguments[2];
    else if (childrenLen > 1) newProps.children = Array.prototype.slice.call(arguments, 2);
    return {
      $$typeof: REACT_ELEMENT_TYPE,
      type: element.type,
      key: config && config.key !== undefined && config.key !== null ? String(config.key) : element.key,
      props: newProps,
    };
  }

  function flattenChildren(children, out) {
    if (children === null || children === undefined || typeof children === "boolean") return;
    if (Array.isArray(children)) {
      for (let i = 0; i < children.length; i++) flattenChildren(children[i], out);
    } else {
      out.push(children);
    }
  }

  const Children = {
    toArray: function (children) {
      const out = [];
      flattenChildren(children, out);
      return out;
    },
    map: function (children, fn, ctx) {
      return Children.toArray(children).map(fn, ctx);
    },
    forEach: function (children, fn, ctx) {
      Children.toArray(children).forEach(fn, ctx);
    },
    count: function (children) {
      return Children.toArray(children).length;
    },
    only: function (children) {
      const arr = Children.toArray(children);
      if (arr.length !== 1) throw new Error("React.Children.only expected a single React element child.");
      return arr[0];
    },
  };

  function createContext(defaultValue) {
    const ctx = {
      $$typeof: CONTEXT_TYPE,
      _currentValue: defaultValue,
      Provider: function ContextProvider(props) {
        return props.children;
      },
      Consumer: function ContextConsumer(props) {
        return typeof props.children === "function" ? props.children(ctx._currentValue) : null;
      },
    };
    ctx.Provider._context = ctx;
    return ctx;
  }

  function depsChanged(prev, next) {
    if (!prev || !next) return true;
    if (prev.length !== next.length) return true;
    for (let i = 0; i < prev.length; i++) {
      if (!Object.is(prev[i], next[i])) return true;
    }
    return false;
  }

  function scheduleRender() {
    if (_renderScheduled || !_isMounted) return;
    _renderScheduled = true;
    queueMicrotask(function () {
      _renderScheduled = false;
      if (!_isMounted) return;
      try {
        commitRoot();
      } catch (err) {
        reportRuntimeError(err, "render", true);
      }
    });
  }

  function useState(initialState) {
    const fiber = _currentFiber;
    const idx = _hookIndex++;
    if (fiber.hooks.length <= idx) {
      const val = typeof initialState === "function" ? initialState() : initialState;
      fiber.hooks.push({ state: val });
    }
    const hook = fiber.hooks[idx];
    const setState = function (action) {
      const next = typeof action === "function" ? action(hook.state) : action;
      if (!Object.is(next, hook.state)) {
        hook.state = next;
        scheduleRender();
      }
    };
    return [hook.state, setState];
  }

  function useReducer(reducer, initialArg, init) {
    const fiber = _currentFiber;
    const idx = _hookIndex++;
    if (fiber.hooks.length <= idx) {
      const val = typeof init === "function" ? init(initialArg) : initialArg;
      fiber.hooks.push({ state: val });
    }
    const hook = fiber.hooks[idx];
    const dispatch = function (action) {
      const next = reducer(hook.state, action);
      if (!Object.is(next, hook.state)) {
        hook.state = next;
        scheduleRender();
      }
    };
    return [hook.state, dispatch];
  }

  function useRef(initialValue) {
    const fiber = _currentFiber;
    const idx = _hookIndex++;
    if (fiber.hooks.length <= idx) {
      fiber.hooks.push({ ref: { current: initialValue } });
    }
    return fiber.hooks[idx].ref;
  }

  function useMemo(factory, deps) {
    const fiber = _currentFiber;
    const idx = _hookIndex++;
    if (fiber.hooks.length <= idx) {
      const value = factory();
      fiber.hooks.push({ value, deps });
      return value;
    }
    const hook = fiber.hooks[idx];
    if (depsChanged(hook.deps, deps)) {
      hook.value = factory();
      hook.deps = deps;
    }
    return hook.value;
  }

  function useCallback(cb, deps) {
    return useMemo(() => cb, deps);
  }

  function useEffect(effect, deps) {
    const fiber = _currentFiber;
    const idx = _hookIndex++;
    if (fiber.hooks.length <= idx) {
      const hook = { deps: undefined, cleanup: undefined, mounted: false };
      fiber.hooks.push(hook);
      _pendingEffects.push(function () {
        hook.deps = deps;
        hook.mounted = true;
        const res = effect();
        if (typeof res === "function") hook.cleanup = res;
      });
    } else {
      const hook = fiber.hooks[idx];
      if (!deps || depsChanged(hook.deps, deps)) {
        _pendingEffects.push(function () {
          if (typeof hook.cleanup === "function") {
            try { hook.cleanup(); } catch (_) {}
            hook.cleanup = undefined;
          }
          hook.deps = deps;
          const res = effect();
          if (typeof res === "function") hook.cleanup = res;
        });
      }
    }
  }

  function useContext(ctx) {
    return ctx._currentValue;
  }

  function useId() {
    const fiber = _currentFiber;
    const idx = _hookIndex++;
    if (fiber.hooks.length <= idx) {
      fiber.hooks.push({ id: ":r" + (_idSeq++) + ":" });
    }
    return fiber.hooks[idx].id;
  }

  function useTransition() {
    return [false, function startTransition(cb) { cb(); }];
  }

  function useDeferredValue(value) {
    return value;
  }

  function useImperativeHandle(ref, createHandle, deps) {
    useEffect(function () {
      const val = createHandle();
      if (typeof ref === "function") {
        ref(val);
        return function () { ref(null); };
      } else if (ref && typeof ref === "object") {
        ref.current = val;
        return function () { ref.current = null; };
      }
    }, deps);
  }

  function useSyncExternalStore(subscribe, getSnapshot) {
    const [val, setVal] = useState(getSnapshot);
    useEffect(function () {
      const check = function () { setVal(getSnapshot()); };
      check();
      return subscribe(check);
    }, [subscribe, getSnapshot]);
    return val;
  }

  // Fiber store keyed by component path
  const _fiberStore = new Map();
  let _seenFiberKeys = new Set();

  function cleanupUnusedFibers() {
    for (const [k, fiber] of _fiberStore.entries()) {
      if (!_seenFiberKeys.has(k)) {
        for (const h of fiber.hooks) {
          if (h && typeof h.cleanup === "function") {
            try { h.cleanup(); } catch (_) {}
          }
        }
        if (fiber.instance && typeof fiber.instance.componentWillUnmount === "function") {
          try { fiber.instance.componentWillUnmount(); } catch (_) {}
        }
        _fiberStore.delete(k);
      }
    }
  }

  function renderNode(node, path) {
    if (node === null || node === undefined || typeof node === "boolean") return [];
    if (typeof node === "string" || typeof node === "number") {
      return [{ $t: "#text", text: String(node) }];
    }
    if (Array.isArray(node)) {
      const out = [];
      for (let i = 0; i < node.length; i++) {
        const res = renderNode(node[i], path + "[" + i + "]");
        for (let j = 0; j < res.length; j++) out.push(res[j]);
      }
      return out;
    }
    if (!isValidElement(node)) {
      return [];
    }

    const type = node.type;
    const props = node.props || {};
    const keyPart = node.key != null ? "#" + node.key : "";

    if (type === FRAGMENT_TYPE) {
      return renderNode(props.children, path + "/F" + keyPart);
    }

    if (typeof type === "object" && type !== null && type.$$typeof === CONTEXT_TYPE) {
      const prevVal = type._currentValue;
      type._currentValue = props.value;
      try {
        return renderNode(props.children, path + "/Ctx" + keyPart);
      } finally {
        type._currentValue = prevVal;
      }
    }

    if (typeof type === "function") {
      if (type._context) {
        const prevVal = type._context._currentValue;
        type._context._currentValue = props.value;
        try {
          return renderNode(props.children, path + "/Ctx" + keyPart);
        } finally {
          type._context._currentValue = prevVal;
        }
      }
      const compName = type.displayName || type.name || "Fn";
      const fiberKey = path + "/" + compName + keyPart;
      _seenFiberKeys.add(fiberKey);
      let fiber = _fiberStore.get(fiberKey);
      if (!fiber || fiber.type !== type) {
        fiber = { hooks: [], type: type, instance: null };
        _fiberStore.set(fiberKey, fiber);
      }

      if (type.prototype && type.prototype.isReactComponent) {
        let inst = fiber.instance;
        const isMount = !inst;
        if (!inst) {
          inst = new type(props);
          inst._reactFiber = fiber;
          fiber.instance = inst;
        } else {
          const prevProps = inst.props;
          const prevState = inst.state;
          inst.props = props;
          if (typeof type.getDerivedStateFromProps === "function") {
            const derived = type.getDerivedStateFromProps(props, inst.state);
            if (derived) inst.state = Object.assign({}, inst.state, derived);
          }
          if (typeof inst.componentDidUpdate === "function") {
            _pendingEffects.push(function () { inst.componentDidUpdate(prevProps, prevState); });
          }
        }
        if (isMount) {
          if (typeof type.getDerivedStateFromProps === "function") {
            const derived = type.getDerivedStateFromProps(props, inst.state);
            if (derived) inst.state = Object.assign({}, inst.state, derived);
          }
          if (typeof inst.componentDidMount === "function") {
            _pendingEffects.push(function () { inst.componentDidMount(); });
          }
        }
        try {
          const rendered = inst.render();
          return renderNode(rendered, fiberKey);
        } catch (err) {
          if (typeof inst.componentDidCatch === "function" || typeof type.getDerivedStateFromError === "function") {
            if (typeof type.getDerivedStateFromError === "function") {
              inst.state = Object.assign({}, inst.state, type.getDerivedStateFromError(err));
            }
            if (typeof inst.componentDidCatch === "function") {
              inst.componentDidCatch(err, { componentStack: fiberKey });
            }
            return renderNode(inst.render(), fiberKey);
          }
          throw err;
        }
      }

      const prevFiber = _currentFiber;
      const prevIdx = _hookIndex;
      _currentFiber = fiber;
      _hookIndex = 0;
      let rendered;
      try {
        rendered = type(props);
      } finally {
        _currentFiber = prevFiber;
        _hookIndex = prevIdx;
      }
      return renderNode(rendered, fiberKey);
    }

    if (typeof type === "object" && type !== null && type._render) {
      const compName = type.displayName || "ForwardRef";
      const fiberKey = path + "/" + compName + keyPart;
      _seenFiberKeys.add(fiberKey);
      let fiber = _fiberStore.get(fiberKey);
      if (!fiber) {
        fiber = { hooks: [], type: type };
        _fiberStore.set(fiberKey, fiber);
      }
      const prevFiber = _currentFiber;
      const prevIdx = _hookIndex;
      _currentFiber = fiber;
      _hookIndex = 0;
      let rendered;
      try {
        rendered = type._render(props, props.ref || null);
      } finally {
        _currentFiber = prevFiber;
        _hookIndex = prevIdx;
      }
      return renderNode(rendered, fiberKey);
    }

    if (typeof type === "string") {
      const inst = { $t: type };
      for (const k of Object.keys(props)) {
        if (k === "children" || k === "key" || k === "ref") continue;
        const v = props[k];
        if (v === undefined) continue;
        if (typeof v === "function") {
          inst[k] = registerCallback(v);
        } else if (isValidElement(v)) {
          // ignore unserialized element prop (wrapper components extract them as children)
        } else {
          inst[k] = v;
        }
      }
      if (props.children !== undefined) {
        const renderedChildren = renderNode(props.children, path + "/" + type + keyPart);
        if (renderedChildren.length > 0) inst.children = renderedChildren;
      }
      return [inst];
    }

    return [];
  }

  function flushEffects() {
    while (_pendingEffects.length > 0) {
      const batch = _pendingEffects.splice(0, _pendingEffects.length);
      for (let i = 0; i < batch.length; i++) {
        try {
          batch[i]();
        } catch (e) {
          reportRuntimeError(e, "useEffect", false);
        }
      }
    }
  }

  function commitRoot() {
    if (!_rootElement) return;
    _prevCallbacks = _callbacks;
    _callbacks = new Map();
    _seenFiberKeys = new Set();
    const children = renderNode(_rootElement, "root");
    cleanupUnusedFibers();
    _rootInstanceTree = { $t: "root", children: children };
    if (typeof global.__emitRender === "function") {
      global.__emitRender(JSON.stringify(_rootInstanceTree));
    }
    flushEffects();
  }

  function renderRoot(element) {
    _isMounted = true;
    _rootElement = element;
    commitRoot();
  }

  function unmountRoot() {
    _isMounted = false;
    _rootElement = null;
    _seenFiberKeys = new Set();
    cleanupUnusedFibers();
    _callbacks.clear();
    _prevCallbacks.clear();
    _activeTimers.clear();
    _activeIntervals.clear();
  }

  const React = {
    Fragment: FRAGMENT_TYPE,
    StrictMode: function StrictMode(props) { return props.children; },
    Suspense: function Suspense(props) { return props.children; },
    Component: Component,
    PureComponent: PureComponent,
    createElement: createElement,
    cloneElement: cloneElement,
    isValidElement: isValidElement,
    createContext: createContext,
    createRef: function () { return { current: null }; },
    Children: Children,
    useState: useState,
    useReducer: useReducer,
    useEffect: useEffect,
    useLayoutEffect: useEffect,
    useMemo: useMemo,
    useCallback: useCallback,
    useRef: useRef,
    useContext: useContext,
    useId: useId,
    useTransition: useTransition,
    useDeferredValue: useDeferredValue,
    useImperativeHandle: useImperativeHandle,
    useSyncExternalStore: useSyncExternalStore,
    forwardRef: function (render) { return { _render: render }; },
    memo: function (comp) { return comp; },
    version: "19.0.0",
  };
  React.default = React;

  global._jsx = jsx;
  global._jsxs = jsx;
  global._jsxFragment = FRAGMENT_TYPE;

  // ---------------------------------------------------------------------------
  // @raycast/api & @vicinae/api Compatible Components & Services
  // ---------------------------------------------------------------------------
  const Color = {
    Blue: "#3B82F6",
    Green: "#22C55E",
    Magenta: "#EC4899",
    Orange: "#F97316",
    Purple: "#A855F7",
    Red: "#EF4444",
    Yellow: "#EAB308",
    PrimaryText: "#E5E7EB",
    SecondaryText: "#9CA3AF",
  };

  const Icon = new Proxy({
    Airplane: "airplane", Alarm: "alarm", AppWindow: "app-window", ArrowClockwise: "arrow-clockwise",
    ArrowDown: "arrow-down", ArrowLeft: "arrow-left", ArrowRight: "arrow-right", ArrowUp: "arrow-up",
    Bell: "bell", Binoculars: "binoculars", Bird: "bird", Bolt: "bolt", Book: "book", Bookmark: "bookmark",
    Box: "box", Brush: "brush", Bug: "bug", Building: "building", BulletPoints: "bullet-points",
    Calculator: "calculator", Calendar: "calendar", Camera: "camera", Cart: "cart",
    Check: "check", CheckCircle: "check-circle", Checkmark: "checkmark", ChevronDown: "chevron-down",
    ChevronLeft: "chevron-left", ChevronRight: "chevron-right", ChevronUp: "chevron-up",
    Circle: "circle", Clipboard: "clipboard", Clock: "clock", Cloud: "cloud", Code: "code",
    CodeBlock: "code-block", Cog: "cog", Coins: "coins", Compass: "compass", ComputerChip: "computer-chip",
    CopyClipboard: "copy-clipboard", CreditCard: "credit-card", Crypto: "crypto",
    Desktop: "desktop", Document: "document", Dot: "dot", Download: "download",
    EditShape: "edit-shape", Ellipsis: "ellipsis", Envelope: "envelope", ExclamationMark: "exclamation-mark",
    Eye: "eye", EyeDisabled: "eye-disabled", FilmStrip: "film-strip", Filter: "filter",
    Finder: "finder", Fingerprint: "fingerprint", Flag: "flag", Folder: "folder",
    Gauge: "gauge", Gear: "gear", Gift: "gift", Globe: "globe",
    Hammer: "hammer", HardDrive: "hard-drive", Hashtag: "hashtag", Heart: "heart",
    House: "house", Image: "image", Info: "info", Key: "key", Keyboard: "keyboard",
    Layers: "layers", Leaf: "leaf", LightBulb: "light-bulb", Link: "link", List: "list",
    Lock: "lock", LockUnlocked: "lock-unlocked", MagnifyingGlass: "magnifying-glass",
    Map: "map", Megaphone: "megaphone", MemoryStick: "memory-stick", Message: "message",
    Microphone: "microphone", Minus: "minus", MinusCircle: "minus-circle", Mobile: "mobile",
    Monitor: "monitor", Moon: "moon", Music: "music", Network: "network", NewDocument: "new-document",
    Number00: "number-00", Paperclip: "paperclip", Pause: "pause", Pencil: "pencil",
    Person: "person", PersonCircle: "person-circle", Phone: "phone", Pin: "pin", Play: "play",
    Plug: "plug", Plus: "plus", PlusCircle: "plus-circle", Power: "power", Print: "print",
    QuestionMark: "question-mark", QuestionMarkCircle: "question-mark-circle", Quotes: "quotes",
    Receipt: "receipt", Repeat: "repeat", Reply: "reply", Rewind: "rewind", Rocket: "rocket",
    RotateAntiClockwise: "rotate-anti-clockwise", RotateClockwise: "rotate-clockwise", Rss: "rss",
    SaveDocument: "save-document", Shield: "shield", Shuffle: "shuffle", Sidebar: "sidebar",
    Snippets: "snippets", Speaker: "speaker", SpeakerOff: "speaker-off", Star: "star",
    StarCircle: "star-circle", Stop: "stop", Store: "store", Sun: "sun", Switch: "switch",
    Syringe: "syringe", Tag: "tag", Terminal: "terminal", Text: "text", TextCursor: "text-cursor",
    ThumbsDown: "thumbs-down", ThumbsUp: "thumbs-up", Trash: "trash", Tray: "tray",
    Tree: "tree", Trophy: "trophy", TwoPeople: "two-people", Umbrella: "umbrella",
    Upload: "upload", Video: "video", Wallet: "wallet", Wand: "wand", Warning: "warning",
    Wifi: "wifi", Window: "window", WrenchScrewdriver: "wrench-screwdriver",
    XMarkCircle: "xmark-circle", Xmark: "xmark",
  }, {
    get: function (target, prop) {
      if (prop in target) return target[prop];
      if (typeof prop === "string") return prop.replace(/([a-z0-9])([A-Z])/g, "$1-$2").toLowerCase();
      return undefined;
    },
  });

  const Image = {
    Mask: { Circle: "Circle", RoundedRectangle: "RoundedRectangle", None: "None" },
  };

  function serializeColorLike(c) {
    if (!c) return undefined;
    if (typeof c === "string") return { raw: c };
    if (typeof c === "object" && (c.light || c.dark)) return { dynamic: c };
    return { raw: String(c) };
  }

  function serializeImageLike(img) {
    if (!img) return undefined;
    if (typeof img === "string") {
      if (img.startsWith("http://") || img.startsWith("https://") || img.startsWith("data:") || img.startsWith("file://") || img.startsWith("/")) {
        return { source: { raw: img } };
      }
      return { source: { raw: "icon://" + img } };
    }
    if (typeof img === "object") {
      if (img.fileIcon) return { fileIcon: String(img.fileIcon) };
      let source;
      if (typeof img.source === "string") {
        source = (img.source.startsWith("http") || img.source.startsWith("data:") || img.source.startsWith("/") || img.source.startsWith("file:"))
          ? { raw: img.source }
          : { raw: "icon://" + img.source };
      } else if (img.source && typeof img.source === "object") {
        source = { themed: img.source };
      }
      return {
        source: source,
        fallback: img.fallback ? serializeImageLike(img.fallback).source : undefined,
        mask: img.mask,
        tintColor: serializeColorLike(img.tintColor),
      };
    }
    return undefined;
  }

  function formatDateValue(d) {
    const dt = d instanceof Date ? d : new Date(d);
    if (isNaN(dt.getTime())) return String(d);
    const diffMs = Date.now() - dt.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMs >= 0 && diffMin < 1) return "Just now";
    if (diffMs >= 0 && diffMin < 60) return diffMin + "m ago";
    const diffHr = Math.floor(diffMin / 60);
    if (diffMs >= 0 && diffHr < 24) return diffHr + "h ago";
    const diffDay = Math.floor(diffHr / 24);
    if (diffMs >= 0 && diffDay < 7) return diffDay + "d ago";
    return dt.toISOString().slice(0, 10);
  }

  const Keyboard = {
    Shortcut: {
      Common: {
        Copy: { modifiers: ["cmd"], key: "c" },
        CopyDeeplink: { modifiers: ["cmd", "shift"], key: "c" },
        CopyName: { modifiers: ["cmd", "shift"], key: "." },
        CopyPath: { modifiers: ["cmd", "shift"], key: "," },
        Duplicate: { modifiers: ["cmd"], key: "d" },
        Edit: { modifiers: ["cmd"], key: "e" },
        MoveDown: { modifiers: ["cmd", "opt"], key: "arrowDown" },
        MoveUp: { modifiers: ["cmd", "opt"], key: "arrowUp" },
        New: { modifiers: ["cmd"], key: "n" },
        Open: { modifiers: ["cmd"], key: "o" },
        OpenWith: { modifiers: ["cmd", "opt"], key: "o" },
        Pin: { modifiers: ["cmd", "shift"], key: "p" },
        Refresh: { modifiers: ["cmd"], key: "r" },
        Remove: { modifiers: ["ctrl"], key: "x" },
        RemoveAll: { modifiers: ["ctrl", "shift"], key: "x" },
        ToggleQuickLook: { modifiers: ["cmd"], key: "y" },
      },
    },
  };

  const LaunchType = {
    UserInitiated: "User",
    Background: "Background",
    CommandLine: "CommandLine",
  };

  const PopToRootType = {
    Default: "Default",
    Immediate: "Immediate",
    Suspended: "Suspended",
  };

  const ToastStyle = {
    Success: "Success",
    Failure: "Error",
    Error: "Error",
    Animated: "Dynamic",
    Dynamic: "Dynamic",
    Info: "Info",
    Warning: "Warning",
  };

  class Toast {
    constructor(opts) {
      this._id = "toast_" + (_cbSeq++);
      this._title = opts && opts.title ? String(opts.title) : "";
      this._message = opts && opts.message ? String(opts.message) : "";
      this._style = (opts && opts.style) || ToastStyle.Success;
      this._primaryAction = opts && opts.primaryAction;
      this._secondaryAction = opts && opts.secondaryAction;
    }
    get title() { return this._title; }
    set title(v) { this._title = String(v || ""); this._sync(); }
    get message() { return this._message; }
    set message(v) { this._message = String(v || ""); this._sync(); }
    get style() { return this._style; }
    set style(v) { this._style = v; this._sync(); }
    _sync() {
      return hostCall("ui.showToast", {
        id: this._id,
        title: this._title,
        message: this._message,
        style: this._style,
        primaryActionTitle: this._primaryAction && this._primaryAction.title,
      });
    }
    show() { return this._sync(); }
    hide() { return hostCall("ui.hideToast", { id: this._id }); }
  }
  Toast.Style = ToastStyle;

  function showToast(optsOrStyle, title, message) {
    let opts;
    if (typeof optsOrStyle === "object" && optsOrStyle !== null) {
      opts = optsOrStyle;
    } else {
      opts = { style: optsOrStyle, title: title || "", message: message || "" };
    }
    const t = new Toast(opts);
    return t.show().then(() => t);
  }

  function showHUD(title) {
    return hostCall("ui.showHud", { title: String(title || "") });
  }

  const Alert = {
    ActionStyle: { Default: "Default", Destructive: "Destructive", Cancel: "Cancel" },
  };

  function confirmAlert(options) {
    const opts = options || {};
    return hostCall("ui.confirmAlert", {
      title: opts.title || "",
      message: opts.message || "",
      primaryAction: opts.primaryAction ? { title: opts.primaryAction.title, style: opts.primaryAction.style || "Default" } : { title: "Confirm", style: "Default" },
      dismissAction: opts.dismissAction ? { title: opts.dismissAction.title, style: opts.dismissAction.style || "Cancel" } : { title: "Cancel", style: "Cancel" },
    }).then(function (confirmed) {
      if (confirmed && opts.primaryAction && typeof opts.primaryAction.onAction === "function") {
        opts.primaryAction.onAction();
      } else if (!confirmed && opts.dismissAction && typeof opts.dismissAction.onAction === "function") {
        opts.dismissAction.onAction();
      }
      return Boolean(confirmed);
    });
  }

  function extractClipboardText(content) {
    if (typeof content === "string" || typeof content === "number" || typeof content === "boolean") {
      return String(content);
    }
    if (content && typeof content === "object") {
      if (content.text !== undefined) return String(content.text);
      if (content.html !== undefined) return String(content.html);
      if (content.file !== undefined) return String(content.file);
    }
    return "";
  }

  const Clipboard = {
    copy: function (content, options) {
      return hostCall("clipboard.copy", { text: extractClipboardText(content), concealed: Boolean(options && options.concealed) });
    },
    paste: function (content) {
      return hostCall("clipboard.paste", { text: extractClipboardText(content) });
    },
    readText: function () { return hostCall("clipboard.readText", {}); },
    read: function () { return hostCall("clipboard.readText", {}).then((t) => ({ text: t || "" })); },
    clear: function () { return hostCall("clipboard.clear", {}); },
  };

  const LocalStorage = {
    getItem: function (key) { return hostCall("storage.getItem", { key: String(key) }); },
    setItem: function (key, value) { return hostCall("storage.setItem", { key: String(key), value: value }); },
    removeItem: function (key) { return hostCall("storage.removeItem", { key: String(key) }); },
    clear: function () { return hostCall("storage.clear", {}); },
    allItems: function () { return hostCall("storage.allItems", {}).then((r) => r || {}); },
  };

  class Cache {
    constructor(opts) {
      this._ns = (opts && opts.namespace) || "default";
    }
    get(key) { return hostCallSync("cache.get", { ns: this._ns, key: String(key) }); }
    set(key, data) { hostCallSync("cache.set", { ns: this._ns, key: String(key), value: String(data) }); }
    has(key) { return this.get(key) !== null && this.get(key) !== undefined; }
    remove(key) { return Boolean(hostCallSync("cache.remove", { ns: this._ns, key: String(key) })); }
    clear() { hostCallSync("cache.clear", { ns: this._ns }); }
  }

  function open(target, application) {
    const appPkg = application && typeof application === "object" ? (application.bundleId || application.path || application.name) : application;
    return hostCall("system.open", { target: String(target), application: appPkg ? String(appPkg) : null });
  }
  function openExtensionPreferences() { return hostCall("ui.openPreferences", { scope: "extension" }); }
  function openCommandPreferences() { return hostCall("ui.openPreferences", { scope: "command" }); }
  function popToRoot(opts) {
    global.__dispatchHostEvent("ui.popToRoot", "{}");
    return hostCall("ui.popToRoot", { clearSearchBar: Boolean(opts && opts.clearSearchBar) });
  }
  function clearSearchBar() { return hostCall("ui.clearSearchBar", {}); }
  function closeMainWindow(opts) { return hostCall("ui.closeMainWindow", opts || {}); }
  function updateCommandMetadata(meta) { return hostCall("ui.updateCommandMetadata", meta || {}); }
  function getApplications(pathArg) { return hostCall("system.getApplications", { path: pathArg || null }).then((r) => r || []); }
  function getDefaultApplication(target) { return hostCall("system.getDefaultApplication", { target: String(target) }); }
  function getFrontmostApplication() { return Promise.resolve({ name: "Komorebi", bundleId: "com.aislingheart.komorebi" }); }
  function getSelectedText() { return Promise.resolve(""); }
  function getSelectedFinderItems() { return Promise.resolve([]); }
  function showInFinder(path) { return open(path); }
  function showInFileBrowser(path) { return open(path); }
  function trash(path) { return hostCall("system.trash", { paths: Array.isArray(path) ? path : [path] }); }
  function launchCommand(opts) { return hostCall("system.launchCommand", opts || {}); }
  function captureException(err) { reportRuntimeError(err, "captureException", false); }

  const AI = {
    Model: {
      "OpenAI_GPT4o": "openai-gpt-4o",
      "OpenAI_GPT4o-mini": "openai-gpt-4o-mini",
      "Anthropic_Claude_Sonnet": "anthropic-claude-sonnet",
      "Anthropic_Claude_Haiku": "anthropic-claude-haiku",
    },
    ask: function () {
      return Promise.reject(new Error("Raycast AI is a Raycast Pro cloud service and is not available in Komorebi on Android."));
    },
  };
  const BrowserExtension = {
    getContent: function () {
      return Promise.reject(new Error("Raycast BrowserExtension is not available on Android."));
    },
    getTabs: function () { return Promise.resolve([]); },
  };
  const WindowManagement = {
    getActiveWindow: function () { return Promise.reject(new Error("Desktop WindowManagement is not supported on Android.")); },
    getWindowsOnActiveDesktop: function () { return Promise.resolve([]); },
  };
  const MenuBarExtra = Object.assign(
    function MenuBarExtra(props) { return props.children || null; },
    {
      Item: function () { return null; },
      Section: function (props) { return props.children || null; },
      Separator: function () { return null; },
      Submenu: function (props) { return props.children || null; },
    }
  );

  // OAuth PKCE Client
  function base64UrlEncode(bytes) {
    let bin = "";
    for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    return global.btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  class PKCEClient {
    constructor(options) {
      this._options = options || {};
      this._storageKey = "oauth_tokens_" + (this._options.providerId || this._options.providerName || "default");
    }
    async authorizationRequest(opts) {
      const verifierBytes = hostCallSync("crypto.randomBytes", { length: 32 }) || [];
      const codeVerifier = base64UrlEncode(new Uint8Array(verifierBytes.length ? verifierBytes : [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]));
      const challengeBytes = hostCallSync("crypto.sha256Bytes", { input: codeVerifier }) || [];
      const codeChallenge = base64UrlEncode(new Uint8Array(challengeBytes));
      const state = hostCallSync("crypto.randomUuid", {}) || ("state_" + Date.now());
      const redirectURI = (opts.extraParameters && opts.extraParameters.redirect_uri) || "komorebi://oauth";
      function buildUrl() {
        const url = new URL(opts.endpoint);
        url.searchParams.set("response_type", "code");
        url.searchParams.set("client_id", opts.clientId);
        url.searchParams.set("redirect_uri", redirectURI);
        if (opts.scope) url.searchParams.set("scope", opts.scope);
        url.searchParams.set("state", state);
        url.searchParams.set("code_challenge", codeChallenge);
        url.searchParams.set("code_challenge_method", "S256");
        if (opts.extraParameters) {
          for (const k of Object.keys(opts.extraParameters)) {
            url.searchParams.set(k, opts.extraParameters[k]);
          }
        }
        return url.toString();
      }
      return {
        url: buildUrl(),
        codeChallenge,
        codeVerifier,
        state,
        redirectURI,
        toURL: buildUrl,
      };
    }
    async authorize(req) {
      const url = typeof req === "string" ? req : (req.url || (typeof req.toURL === "function" ? req.toURL() : ""));
      return hostCall("oauth.authorize", { url, providerName: this._options.providerName || "OAuth" });
    }
    async setTokens(tokens) {
      return LocalStorage.setItem(this._storageKey, JSON.stringify(Object.assign({ updatedAt: Date.now() }, tokens)));
    }
    async getTokens() {
      const raw = await LocalStorage.getItem(this._storageKey);
      if (!raw) return undefined;
      try { return typeof raw === "string" ? JSON.parse(raw) : raw; } catch (_) { return undefined; }
    }
    async removeTokens() {
      return LocalStorage.removeItem(this._storageKey);
    }
  }
  const OAuth = {
    PKCEClient: PKCEClient,
    RedirectMethod: { Web: "web", App: "app", AppURI: "appURI" },
  };

  // Environment & Preferences
  const environment = {
    appearance: "dark",
    theme: "dark",
    textSize: "medium",
    isDevelopment: false,
    commandName: "",
    commandMode: "view",
    extensionName: "",
    ownerOrAuthorName: "",
    supportPath: "",
    assetsPath: "",
    raycastVersion: "1.80.0",
    isRaycast: true,
    launchType: LaunchType.UserInitiated,
    canAccess: function (api) {
      if (api === AI || api === BrowserExtension || api === WindowManagement) return false;
      return true;
    },
  };

  let _preferences = {};
  function getPreferenceValues() {
    return Object.assign({}, _preferences);
  }

  // Navigation context
  const NavigationContext = createContext({
    push: function () {},
    pop: function () {},
  });

  function useNavigation() {
    return useContext(NavigationContext);
  }

  function NavigationProvider(props) {
    const [stack, setStack] = useState([props.root]);
    const push = useCallback(function (node) {
      setStack((prev) => prev.concat([node]));
      hostCall("ui.pushView", {});
    }, []);
    const pop = useCallback(function () {
      setStack((prev) => (prev.length > 1 ? prev.slice(0, -1) : prev));
      hostCall("ui.popView", {});
    }, []);

    useEffect(function () {
      const subPop = subscribeEvent("ui.viewPopped", function () {
        setStack((prev) => (prev.length > 1 ? prev.slice(0, -1) : prev));
      });
      const subRoot = subscribeEvent("ui.popToRoot", function () {
        setStack((prev) => (prev.length > 1 ? [prev[0]] : prev));
      });
      return function () {
        subPop.unsubscribe();
        subRoot.unsubscribe();
      };
    }, []);

    return createElement(
      NavigationContext.Provider,
      { value: { push, pop } },
      stack.map((el, idx) => createElement("view", { key: idx }, el))
    );
  }

  // Shared Metadata component
  function MetadataTagListItem(props) {
    return createElement("list-item-detail-metadata-tag-list-item", {
      text: props.text != null ? String(props.text) : "",
      color: serializeColorLike(props.color),
      icon: serializeImageLike(props.icon),
      onAction: props.onAction,
    });
  }
  function MetadataTagList(props) {
    return createElement("list-item-detail-metadata-tag-list", { title: props.title }, props.children);
  }
  MetadataTagList.Item = MetadataTagListItem;

  const Metadata = Object.assign(
    function MetadataRoot(props) {
      return createElement("list-item-detail-metadata", {}, props.children);
    },
    {
      Label: function MetadataLabel(props) {
        const textObj = typeof props.text === "object" && props.text !== null ? props.text : null;
        return createElement("list-item-detail-metadata-label", {
          title: props.title,
          text: textObj ? (textObj.value != null ? String(textObj.value) : "") : (props.text != null ? String(props.text) : ""),
          textColor: textObj && textObj.color ? serializeColorLike(textObj.color) : undefined,
          icon: serializeImageLike(props.icon),
        });
      },
      Link: function MetadataLink(props) {
        return createElement("list-item-detail-metadata-link", {
          title: props.title,
          text: props.text,
          target: props.target,
        });
      },
      Separator: function MetadataSeparator() {
        return createElement("list-item-detail-metadata-separator", {});
      },
      TagList: MetadataTagList,
    }
  );

  // Action & ActionPanel
  function ActionPanelRoot(props) {
    return createElement("action-panel", { title: props.title }, props.children);
  }
  const ActionPanel = Object.assign(ActionPanelRoot, {
    Section: function ActionPanelSection(props) {
      return createElement("action-panel-section", { title: props.title }, props.children);
    },
    Submenu: function ActionPanelSubmenu(props) {
      return createElement("action-panel-submenu", {
        title: props.title,
        icon: serializeImageLike(props.icon),
        onOpen: props.onOpen,
      }, props.children);
    },
  });

  function BaseAction(props) {
    return createElement("action", {
      title: props.title,
      icon: serializeImageLike(props.icon),
      style: props.style || "regular",
      shortcut: props.shortcut,
      onAction: props.onAction,
    });
  }

  const Action = Object.assign(BaseAction, {
    Style: { Regular: "regular", Destructive: "destructive" },
    OpenInBrowser: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Open in Browser",
        icon: props.icon || Icon.Globe,
        shortcut: props.shortcut,
        onAction: function () { open(props.url); if (props.onOpen) props.onOpen(props.url); },
      });
    },
    Open: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Open",
        icon: props.icon || Icon.AppWindow,
        shortcut: props.shortcut,
        onAction: function () { open(props.target, props.application); if (props.onOpen) props.onOpen(props.target); },
      });
    },
    OpenWith: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Open With",
        icon: props.icon || Icon.AppWindow,
        shortcut: props.shortcut,
        onAction: function () { open(props.path, props.application); if (props.onOpen) props.onOpen(props.path); },
      });
    },
    CopyToClipboard: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Copy to Clipboard",
        icon: props.icon || Icon.Clipboard,
        shortcut: props.shortcut,
        onAction: function () {
          Clipboard.copy(props.content, { concealed: props.concealed });
          showHUD("Copied to Clipboard");
          if (props.onCopy) props.onCopy(props.content);
        },
      });
    },
    Paste: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Paste",
        icon: props.icon || Icon.Clipboard,
        shortcut: props.shortcut,
        onAction: function () {
          Clipboard.paste(props.content);
          if (props.onPaste) props.onPaste(props.content);
        },
      });
    },
    Push: function (props) {
      const nav = useNavigation();
      return createElement(BaseAction, {
        title: props.title,
        icon: props.icon || Icon.ArrowRight,
        shortcut: props.shortcut,
        onAction: function () {
          if (props.onPush) props.onPush();
          nav.push(props.target);
        },
      });
    },
    Pop: function (props) {
      const nav = useNavigation();
      return createElement(BaseAction, {
        title: props.title || "Back",
        icon: props.icon || Icon.ArrowLeft,
        shortcut: props.shortcut,
        onAction: function () {
          if (props.onPop) props.onPop();
          nav.pop();
        },
      });
    },
    ShowInFinder: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Open File",
        icon: props.icon || Icon.Finder,
        shortcut: props.shortcut,
        onAction: function () { open(props.path); if (props.onShow) props.onShow(props.path); },
      });
    },
    Trash: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Move to Trash",
        icon: props.icon || Icon.Trash,
        style: "destructive",
        shortcut: props.shortcut,
        onAction: function () {
          Promise.resolve(trash(props.paths)).then(function () {
            if (props.onTrash) props.onTrash(props.paths);
          });
        },
      });
    },
    PickDate: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Pick Date",
        icon: props.icon || Icon.Calendar,
        shortcut: props.shortcut,
        onAction: function () {
          if (props.onChange) props.onChange(new Date());
        },
      });
    },
    SubmitForm: function (props) {
      return createElement("action", {
        title: props.title || "Submit",
        icon: serializeImageLike(props.icon || Icon.Check),
        style: props.style || "regular",
        shortcut: props.shortcut,
        isSubmit: true,
        onSubmit: props.onSubmit,
        onAction: props.onSubmit,
      });
    },
    CreateQuicklink: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Copy Quicklink",
        icon: props.icon || Icon.Link,
        shortcut: props.shortcut,
        onAction: function () {
          if (props.quicklink && props.quicklink.link) Clipboard.copy(props.quicklink.link);
        },
      });
    },
    ToggleQuickLook: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Quick Look",
        icon: props.icon || Icon.Eye,
        shortcut: props.shortcut,
        onAction: function () {},
      });
    },
  });

  // Dropdown
  const Dropdown = Object.assign(
    function DropdownRoot(props) {
      return createElement("dropdown", {
        tooltip: props.tooltip,
        placeholder: props.placeholder,
        value: props.value,
        defaultValue: props.defaultValue,
        isLoading: props.isLoading,
        filtering: props.filtering !== undefined ? Boolean(props.filtering) : undefined,
        onChange: props.onChange,
        onSearchTextChange: props.onSearchTextChange,
      }, props.children);
    },
    {
      Section: function (props) {
        return createElement("dropdown-section", { title: props.title }, props.children);
      },
      Item: function (props) {
        return createElement("dropdown-item", {
          title: props.title,
          value: props.value,
          icon: serializeImageLike(props.icon),
          keywords: props.keywords,
        });
      },
    }
  );

  // List & Grid & Detail & Form
  function serializeAccessories(accessories) {
    if (!Array.isArray(accessories)) return undefined;
    return accessories.map(function (acc) {
      if (!acc) return null;
      let textObj;
      if (typeof acc.text === "string" || typeof acc.text === "number") {
        textObj = { value: String(acc.text) };
      } else if (acc.text && typeof acc.text === "object") {
        textObj = {
          value: acc.text.value != null ? String(acc.text.value) : "",
          color: serializeColorLike(acc.text.color),
        };
      } else if (acc.date !== undefined && acc.date !== null) {
        textObj = { value: formatDateValue(acc.date) };
      }

      let tagObj;
      if (acc.tag !== undefined && acc.tag !== null) {
        if (acc.tag instanceof Date) {
          tagObj = { value: formatDateValue(acc.tag) };
        } else if (typeof acc.tag === "string" || typeof acc.tag === "number") {
          tagObj = { value: String(acc.tag) };
        } else if (typeof acc.tag === "object") {
          const tv = acc.tag.value instanceof Date ? formatDateValue(acc.tag.value) : String(acc.tag.value != null ? acc.tag.value : "");
          tagObj = { value: tv, color: serializeColorLike(acc.tag.color) };
        }
      }

      return {
        text: textObj,
        tag: tagObj,
        icon: serializeImageLike(acc.icon),
        date: acc.date ? formatDateValue(acc.date) : undefined,
        tooltip: acc.tooltip,
      };
    }).filter(Boolean);
  }

  function EmptyView(props) {
    return createElement("empty-view", {
      title: props.title,
      description: props.description,
      icon: serializeImageLike(props.icon),
    }, props.actions);
  }

  const ListItem = Object.assign(
    function ListItem(props) {
      return createElement("list-item", {
        id: props.id,
        title: typeof props.title === "string" || typeof props.title === "number" ? String(props.title) : ((props.title && props.title.value) || ""),
        subtitle: typeof props.subtitle === "string" || typeof props.subtitle === "number" ? String(props.subtitle) : (props.subtitle && props.subtitle.value),
        icon: serializeImageLike(props.icon),
        keywords: props.keywords,
        accessories: serializeAccessories(props.accessories),
      }, props.actions, props.detail);
    },
    {
      Detail: Object.assign(
        function ListItemDetail(props) {
          return createElement("list-item-detail", {
            markdown: props.markdown,
            isLoading: props.isLoading,
          }, props.metadata);
        },
        { Metadata: Metadata }
      ),
    }
  );

  const List = Object.assign(
    function ListRoot(props) {
      const filteringVal = props.filtering !== undefined
        ? Boolean(props.filtering)
        : (props.enableFiltering !== undefined ? Boolean(props.enableFiltering) : !props.onSearchTextChange);
      return createElement("list", {
        isLoading: props.isLoading,
        isShowingDetail: props.isShowingDetail,
        selectedItemId: props.selectedItemId,
        searchText: props.searchText,
        searchBarPlaceholder: props.searchBarPlaceholder,
        navigationTitle: props.navigationTitle,
        filtering: filteringVal,
        throttle: props.throttle,
        onSearchTextChange: props.onSearchTextChange,
        onSelectionChange: props.onSelectionChange,
        onLoadMore: props.pagination && props.pagination.onLoadMore,
        hasMore: Boolean(props.pagination && props.pagination.hasMore),
      }, props.searchBarAccessory, props.actions, props.children);
    },
    {
      Item: ListItem,
      Section: function (props) {
        return createElement("list-section", { title: props.title, subtitle: props.subtitle }, props.children);
      },
      EmptyView: EmptyView,
      Dropdown: Dropdown,
    }
  );

  const Grid = Object.assign(
    function GridRoot(props) {
      let cols = props.columns;
      if (cols === undefined && props.itemSize) {
        cols = props.itemSize === "small" ? 4 : (props.itemSize === "large" ? 2 : 3);
      }
      const filteringVal = props.filtering !== undefined ? Boolean(props.filtering) : !props.onSearchTextChange;
      return createElement("grid", {
        columns: cols,
        itemSize: props.itemSize,
        aspectRatio: props.aspectRatio || "1",
        fit: props.fit || "contain",
        inset: props.inset,
        isLoading: props.isLoading,
        searchText: props.searchText,
        searchBarPlaceholder: props.searchBarPlaceholder,
        navigationTitle: props.navigationTitle,
        filtering: filteringVal,
        onSearchTextChange: props.onSearchTextChange,
        onSelectionChange: props.onSelectionChange,
      }, props.searchBarAccessory, props.actions, props.children);
    },
    {
      Item: function (props) {
        let content = props.content;
        if (content && typeof content === "object" && content.color) {
          content = { color: serializeColorLike(content.color) };
        } else if (content && typeof content === "object" && content.value !== undefined) {
          content = serializeImageLike(content.value);
        } else {
          content = serializeImageLike(content);
        }
        return createElement("grid-item", {
          id: props.id,
          title: props.title,
          subtitle: props.subtitle,
          keywords: props.keywords,
          content: content,
        }, props.actions);
      },
      Section: function (props) {
        return createElement("grid-section", {
          title: props.title,
          subtitle: props.subtitle,
          columns: props.columns,
          aspectRatio: props.aspectRatio,
        }, props.children);
      },
      EmptyView: EmptyView,
      Dropdown: Dropdown,
      Fit: { Contain: "contain", Fill: "fill" },
      Inset: { Zero: "zero", Small: "small", Medium: "medium", Large: "large" },
      ItemSize: { Small: "small", Medium: "medium", Large: "large" },
    }
  );

  const Detail = Object.assign(
    function DetailRoot(props) {
      return createElement("detail", {
        markdown: props.markdown || "",
        navigationTitle: props.navigationTitle,
        isLoading: props.isLoading,
      }, props.actions, props.metadata);
    },
    { Metadata: Metadata }
  );

  function serializeFormValue(v) {
    if (v instanceof Date) return isNaN(v.getTime()) ? "" : v.toISOString();
    return v;
  }

  function formFieldProps(props, extra) {
    return Object.assign({
      id: props.id,
      title: props.title,
      info: props.info,
      error: props.error,
      value: serializeFormValue(props.value),
      defaultValue: serializeFormValue(props.defaultValue),
      hasControlledValue: props.value !== undefined,
      autoFocus: props.autoFocus,
      onChange: props.onChange,
    }, extra || {});
  }

  const Form = Object.assign(
    function FormRoot(props) {
      return createElement("form", {
        navigationTitle: props.navigationTitle,
        isLoading: props.isLoading,
        enableDrafts: props.enableDrafts,
      }, props.actions, props.children);
    },
    {
      TextField: function (props) { return createElement("form-text-field", formFieldProps(props, { placeholder: props.placeholder })); },
      PasswordField: function (props) { return createElement("form-password-field", formFieldProps(props, { placeholder: props.placeholder })); },
      TextArea: function (props) { return createElement("form-text-area", formFieldProps(props, { placeholder: props.placeholder })); },
      Checkbox: function (props) { return createElement("form-checkbox", formFieldProps(props, { label: props.label })); },
      DatePicker: Object.assign(
        function (props) { return createElement("form-date-picker", formFieldProps(props, { type: props.type })); },
        {
          Type: { Date: "date", DateTime: "dateTime" },
          isFullDay: function (date) {
            if (!(date instanceof Date)) return false;
            return date.getHours() === 0 && date.getMinutes() === 0 && date.getSeconds() === 0;
          },
        }
      ),
      Dropdown: Object.assign(
        function (props) { return createElement("form-dropdown", formFieldProps(props, { placeholder: props.placeholder }), props.children); },
        {
          Section: function (props) { return createElement("dropdown-section", { title: props.title }, props.children); },
          Item: function (props) { return createElement("dropdown-item", { title: props.title, value: props.value, icon: serializeImageLike(props.icon) }); },
        }
      ),
      TagPicker: Object.assign(
        function (props) { return createElement("form-tag-picker", formFieldProps(props), props.children); },
        { Item: function (props) { return createElement("dropdown-item", { title: props.title, value: props.value, icon: serializeImageLike(props.icon) }); } }
      ),
      FilePicker: function (props) {
        const val = Array.isArray(props.value) ? props.value[0] : props.value;
        const defVal = Array.isArray(props.defaultValue) ? props.defaultValue[0] : props.defaultValue;
        return createElement("form-text-field", formFieldProps(Object.assign({}, props, { value: val, defaultValue: defVal }), { placeholder: "File path..." }));
      },
      Separator: function () { return createElement("form-separator", {}); },
      Description: function (props) { return createElement("form-description", { title: props.title, text: props.text }); },
      LinkAccessory: function (props) { return createElement("form-description", { title: props.text, text: props.target }); },
    }
  );

  const raycastApi = {
    AI, Action, ActionPanel, Alert, BrowserExtension, Cache, Clipboard, Color, Detail, Dropdown,
    Environment: environment, environment, Form, Grid, Icon, Image, Keyboard, LaunchType, List,
    LocalStorage, MenuBarExtra, OAuth, PopToRootType, Toast, WindowManagement,
    captureException, clearSearchBar, closeMainWindow, confirmAlert, getApplications,
    getDefaultApplication, getFrontmostApplication, getPreferenceValues, getSelectedFinderItems,
    getSelectedText, launchCommand, open, openCommandPreferences, openExtensionPreferences,
    popToRoot, showHUD, showInFileBrowser, showInFinder, showToast, trash, updateCommandMetadata,
    useNavigation,
  };

  // ---------------------------------------------------------------------------
  // @raycast/utils compatibility helpers
  // ---------------------------------------------------------------------------
  function usePromise(fn, args, options) {
    let deps = [];
    let opts = {};
    if (Array.isArray(args)) {
      deps = args;
      opts = options || {};
    } else if (args && typeof args === "object") {
      opts = args;
    } else if (options) {
      opts = options;
    }
    const execute = opts.execute !== undefined ? Boolean(opts.execute) : true;
    const [state, setState] = useState({ data: opts.initialData, isLoading: execute, error: undefined });
    const runIdRef = useRef(0);
    const fnRef = useRef(fn);
    const depsRef = useRef(deps);
    const optsRef = useRef(opts);
    fnRef.current = fn;
    depsRef.current = deps;
    optsRef.current = opts;

    const revalidate = useCallback(function () {
      const id = ++runIdRef.current;
      const callArgs = arguments.length > 0 ? Array.prototype.slice.call(arguments) : depsRef.current;
      setState((prev) => ({ data: prev.data, isLoading: true, error: undefined }));
      return Promise.resolve().then(() => fnRef.current.apply(null, callArgs)).then(
        function (data) {
          if (id === runIdRef.current) {
            setState({ data, isLoading: false, error: undefined });
            if (optsRef.current.onData) optsRef.current.onData(data);
          }
          return data;
        },
        function (error) {
          if (id === runIdRef.current) {
            setState((prev) => ({ data: prev.data, isLoading: false, error }));
            if (optsRef.current.onError) {
              optsRef.current.onError(error);
            } else if (optsRef.current.failureToastOptions) {
              showFailureToast(error, optsRef.current.failureToastOptions);
            }
          }
        }
      );
    }, deps);

    useEffect(function () {
      if (execute) revalidate();
    }, [execute].concat(deps));

    return {
      data: state.data,
      isLoading: state.isLoading,
      error: state.error,
      revalidate: revalidate,
      mutate: function (asyncUpdate, mOpts) {
        const prevData = state.data;
        const shouldRevalidate = !mOpts || mOpts.shouldRevalidateAfter !== false;
        if (mOpts && typeof mOpts.optimisticUpdate === "function") {
          setState((prev) => ({ data: mOpts.optimisticUpdate(prev.data), isLoading: prev.isLoading, error: prev.error }));
        }
        return Promise.resolve(typeof asyncUpdate === "function" ? asyncUpdate() : asyncUpdate).then(
          function () { return shouldRevalidate ? revalidate() : state.data; },
          function (err) {
            if (!mOpts || mOpts.rollbackOnError !== false) {
              setState((prev) => ({ data: prevData, isLoading: false, error: err }));
            }
            throw err;
          }
        );
      },
    };
  }

  function useFetch(url, options) {
    const opts = options || {};
    const resolvedUrl = typeof url === "function" ? url({ page: 0 }) : url;
    const headersKey = opts.headers ? JSON.stringify(opts.headers) : "";
    const bodyKey = opts.body !== undefined ? String(opts.body) : "";
    const res = usePromise(
      function (u) {
        return global.fetch(u, opts).then(function (r) {
          if (!r.ok) throw new Error("HTTP " + r.status + " " + r.statusText);
          if (opts.parseResponse) return opts.parseResponse(r);
          return r.json();
        }).then(function (parsed) {
          if (typeof opts.mapResult === "function") {
            const mapped = opts.mapResult(parsed);
            return mapped && mapped.data !== undefined ? mapped.data : mapped;
          }
          return parsed;
        });
      },
      [resolvedUrl, opts.method || "GET", headersKey, bodyKey],
      opts
    );
    return res;
  }

  function useCachedState(key, initialValue) {
    const cache = useMemo(() => new Cache({ namespace: "useCachedState" }), []);
    const [val, setVal] = useState(function () {
      const raw = cache.get(key);
      if (raw != null) { try { return JSON.parse(raw); } catch (_) {} }
      return typeof initialValue === "function" ? initialValue() : initialValue;
    });
    const setCached = useCallback(function (next) {
      setVal((prev) => {
        const resolved = typeof next === "function" ? next(prev) : next;
        try { cache.set(key, JSON.stringify(resolved)); } catch (_) {}
        return resolved;
      });
    }, [key]);
    return [val, setCached];
  }

  function useLocalStorage(key, initialValue) {
    const [value, setValue] = useState(initialValue);
    const [isLoading, setIsLoading] = useState(true);
    useEffect(function () {
      LocalStorage.getItem(key).then(function (raw) {
        if (raw !== undefined && raw !== null) {
          try { setValue(typeof raw === "string" ? JSON.parse(raw) : raw); } catch (_) { setValue(raw); }
        }
        setIsLoading(false);
      });
    }, [key]);
    const set = useCallback(function (v) {
      setValue(v);
      return LocalStorage.setItem(key, JSON.stringify(v));
    }, [key]);
    const remove = useCallback(function () {
      setValue(initialValue);
      return LocalStorage.removeItem(key);
    }, [key]);
    return { value, setValue: set, removeValue: remove, isLoading };
  }

  function useForm(options) {
    const opts = options || {};
    const initial = opts.initialValues || {};
    const [values, setValues] = useState(function () { return Object.assign({}, initial); });
    const [errors, setErrors] = useState({});
    const validation = opts.validation || {};

    function validateField(name, val, allVals) {
      const rule = validation[name];
      if (typeof rule === "function") return rule(val);
      if (rule === "required") {
        if (val === undefined || val === null || val === "" || (Array.isArray(val) && val.length === 0)) {
          return "The item is required";
        }
      }
      return undefined;
    }

    const setValue = useCallback(function (name, val) {
      setValues((prev) => Object.assign({}, prev, { [name]: val }));
      setErrors((prev) => Object.assign({}, prev, { [name]: undefined }));
    }, []);

    const setValidationError = useCallback(function (name, err) {
      setErrors((prev) => Object.assign({}, prev, { [name]: err }));
    }, []);

    const reset = useCallback(function (nextInitial) {
      setValues(Object.assign({}, nextInitial || initial));
      setErrors({});
    }, []);

    const handleSubmit = useCallback(function (submittedValues) {
      const merged = Object.assign({}, values, submittedValues || {});
      const nextErrors = {};
      let hasError = false;
      for (const k of Object.keys(validation)) {
        const err = validateField(k, merged[k], merged);
        if (err) {
          nextErrors[k] = err;
          hasError = true;
        }
      }
      setErrors(nextErrors);
      if (hasError) return false;
      if (typeof opts.onSubmit === "function") return opts.onSubmit(merged);
      return true;
    }, [values, opts.onSubmit]);

    const itemProps = new Proxy({}, {
      get: function (_, fieldName) {
        if (typeof fieldName !== "string") return undefined;
        return {
          id: fieldName,
          value: values[fieldName],
          error: errors[fieldName],
          onChange: function (v) { setValue(fieldName, v); },
          onBlur: function () {
            const err = validateField(fieldName, values[fieldName], values);
            if (err) setValidationError(fieldName, err);
          },
        };
      },
    });

    return {
      handleSubmit,
      itemProps,
      values,
      setValue,
      setValidationError,
      reset,
      focus: function () {},
    };
  }

  let _currentOAuthAccessToken = null;
  let _currentOAuthType = "oauth";

  class OAuthService {
    constructor(options) {
      this._options = options || {};
      this.client = options.client || new PKCEClient({
        providerName: options.clientId || "OAuth",
        providerId: options.clientId || "oauth",
      });
    }
    async authorize() {
      if (this._options.personalAccessToken) {
        _currentOAuthAccessToken = this._options.personalAccessToken;
        _currentOAuthType = "personal";
        return this._options.personalAccessToken;
      }
      const existing = await this.client.getTokens();
      if (existing && existing.accessToken) {
        _currentOAuthAccessToken = existing.accessToken;
        _currentOAuthType = "oauth";
        return existing.accessToken;
      }
      if (typeof this._options.authorize === "function") {
        const token = await this._options.authorize();
        _currentOAuthAccessToken = token;
        _currentOAuthType = "oauth";
        return token;
      }
      const req = await this.client.authorizationRequest({
        endpoint: this._options.authorizeUrl,
        clientId: this._options.clientId,
        scope: this._options.scope || "",
        extraParameters: this._options.extraParameters,
      });
      const res = await this.client.authorize(req);
      if (res && res.authorizationCode && this._options.tokenUrl) {
        const params = new URLSearchParams({
          client_id: this._options.clientId,
          code: res.authorizationCode,
          code_verifier: req.codeVerifier,
          grant_type: "authorization_code",
          redirect_uri: req.redirectURI,
        });
        const tokResp = await global.fetch(this._options.tokenUrl, {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
          body: params.toString(),
        });
        const tokJson = await tokResp.json();
        const token = tokJson.access_token || tokJson.accessToken;
        if (token) {
          await this.client.setTokens({ accessToken: token, refreshToken: tokJson.refresh_token, expiresIn: tokJson.expires_in });
          _currentOAuthAccessToken = token;
          return token;
        }
      }
      throw new Error("OAuth authorization did not yield an access token.");
    }
  }
  OAuthService.github = function (opts) {
    return new OAuthService(Object.assign({
      authorizeUrl: "https://github.com/login/oauth/authorize",
      tokenUrl: "https://github.com/login/oauth/access_token",
    }, opts));
  };
  OAuthService.linear = function (opts) {
    return new OAuthService(Object.assign({
      authorizeUrl: "https://linear.app/oauth/authorize",
      tokenUrl: "https://api.linear.app/oauth/token",
    }, opts));
  };
  OAuthService.slack = function (opts) {
    return new OAuthService(Object.assign({
      authorizeUrl: "https://slack.com/oauth/v2/authorize",
      tokenUrl: "https://slack.com/api/oauth.v2.access",
    }, opts));
  };
  OAuthService.google = function (opts) {
    return new OAuthService(Object.assign({
      authorizeUrl: "https://accounts.google.com/o/oauth2/v2/auth",
      tokenUrl: "https://oauth2.googleapis.com/token",
    }, opts));
  };
  OAuthService.jira = function (opts) {
    return new OAuthService(Object.assign({
      authorizeUrl: "https://auth.atlassian.com/authorize",
      tokenUrl: "https://auth.atlassian.com/oauth/token",
    }, opts));
  };
  OAuthService.zoom = function (opts) {
    return new OAuthService(Object.assign({
      authorizeUrl: "https://zoom.us/oauth/authorize",
      tokenUrl: "https://zoom.us/oauth/token",
    }, opts));
  };
  OAuthService.asana = function (opts) {
    return new OAuthService(Object.assign({
      authorizeUrl: "https://app.asana.com/-/oauth_authorize",
      tokenUrl: "https://app.asana.com/-/oauth_token",
    }, opts));
  };

  function withAccessToken(provider) {
    return function (ComponentOrFn) {
      return function AccessTokenWrapper(props) {
        const [ready, setReady] = useState(Boolean(_currentOAuthAccessToken || (provider && provider.personalAccessToken)));
        const [authErr, setAuthErr] = useState(null);
        useEffect(function () {
          let cancelled = false;
          Promise.resolve(provider && typeof provider.authorize === "function" ? provider.authorize() : null).then(
            function (token) {
              if (!cancelled) {
                if (token) _currentOAuthAccessToken = token;
                if (provider && typeof provider.onAuthorize === "function") {
                  provider.onAuthorize({ token: _currentOAuthAccessToken, type: _currentOAuthType });
                }
                setReady(true);
              }
            },
            function (e) { if (!cancelled) setAuthErr(e); }
          );
          return function () { cancelled = true; };
        }, []);

        if (authErr) {
          return createElement(Detail, {
            markdown: "# Authorization Failed\n\n" + (authErr.message || String(authErr)),
          });
        }
        if (!ready) {
          return createElement(List, { isLoading: true });
        }
        return createElement(ComponentOrFn, props);
      };
    };
  }

  function getAccessToken() {
    return { token: _currentOAuthAccessToken || "", type: _currentOAuthType };
  }

  function showFailureToast(error, options) {
    const msg = error instanceof Error ? error.message : String(error || "Unknown error");
    return showToast({
      style: ToastStyle.Failure,
      title: (options && options.title) || "Something went wrong",
      message: (options && options.message) || msg,
    });
  }

  const raycastUtils = {
    usePromise,
    useCachedPromise: usePromise,
    useFetch,
    useCachedState,
    useLocalStorage,
    useForm,
    OAuthService,
    withAccessToken,
    getAccessToken,
    showFailureToast,
    runAppleScript: function () {
      return Promise.reject(new Error("AppleScript is macOS-only and cannot run on Android."));
    },
    useExec: function (cmd) {
      return {
        isLoading: false,
        data: undefined,
        error: new Error("Executing native process '" + cmd + "' is not supported on Android."),
        revalidate: function () { return Promise.resolve(); },
      };
    },
    executeSQL: function () {
      return Promise.reject(new Error("Local SQLite binary execution is not supported in the JS sandbox."));
    },
    useSQL: function () {
      return {
        isLoading: false,
        data: undefined,
        error: new Error("useSQL is not supported on Android."),
        revalidate: function () {},
      };
    },
    getFavicon: function (url) {
      try {
        const u = new URL(url);
        return "https://www.google.com/s2/favicons?sz=64&domain=" + encodeURIComponent(u.hostname);
      } catch (_) {
        return Icon.Globe;
      }
    },
    getAvatarIcon: function () {
      return Icon.PersonCircle;
    },
    getProgressIcon: function (progress) {
      if (progress >= 1) return Icon.CheckCircle;
      if (progress <= 0) return Icon.Circle;
      return Icon.Clock;
    },
  };

  // ---------------------------------------------------------------------------
  // Module Loader / CommonJS require() with Node Core Polyfills
  // ---------------------------------------------------------------------------
  const _moduleCache = {};
  const _fsMap = {};

  function normalizePath(p) {
    const parts = String(p).replace(/\\/g, "/").split("/");
    const stack = [];
    for (const part of parts) {
      if (!part || part === ".") continue;
      if (part === "..") stack.pop();
      else stack.push(part);
    }
    return "/" + stack.join("/");
  }

  function dirname(p) {
    const idx = p.lastIndexOf("/");
    return idx <= 0 ? "/" : p.slice(0, idx);
  }

  function isAbsolutePath(p) {
    return String(p || "").startsWith("/");
  }

  function resolvePath() {
    let resolved = "";
    for (let i = arguments.length - 1; i >= 0; i--) {
      const seg = arguments[i];
      if (!seg) continue;
      const s = String(seg).replace(/\\/g, "/");
      resolved = resolved ? (s.endsWith("/") ? s + resolved : s + "/" + resolved) : s;
      if (s.startsWith("/")) break;
    }
    return normalizePath(resolved || "/");
  }

  function relativePath(from, to) {
    const f = normalizePath(from).split("/").filter(Boolean);
    const t = normalizePath(to).split("/").filter(Boolean);
    let i = 0;
    while (i < f.length && i < t.length && f[i] === t[i]) i++;
    const up = [];
    for (let j = i; j < f.length; j++) up.push("..");
    return up.concat(t.slice(i)).join("/") || ".";
  }

  function makeFsError(code, pathStr, msg) {
    const e = new Error(code + ": " + msg + ", '" + pathStr + "'");
    e.code = code;
    e.path = pathStr;
    return e;
  }

  const nodePath = {
    join: function () { return normalizePath(Array.prototype.join.call(arguments, "/")); },
    resolve: resolvePath,
    normalize: normalizePath,
    isAbsolute: isAbsolutePath,
    relative: relativePath,
    dirname: dirname,
    basename: function (p, ext) {
      const base = String(p).split("/").pop() || "";
      return ext && base.endsWith(ext) ? base.slice(0, -ext.length) : base;
    },
    extname: function (p) {
      const base = String(p).split("/").pop() || "";
      const i = base.lastIndexOf(".");
      return i > 0 ? base.slice(i) : "";
    },
    parse: function (p) {
      const norm = normalizePath(p);
      const base = nodePath.basename(norm);
      const ext = nodePath.extname(base);
      const name = ext ? base.slice(0, -ext.length) : base;
      return { root: "/", dir: dirname(norm), base: base, ext: ext, name: name };
    },
    format: function (obj) {
      if (!obj) return "";
      const dir = obj.dir || obj.root || "";
      const base = obj.base || ((obj.name || "") + (obj.ext || ""));
      return dir ? (dir === "/" ? "/" + base : dir + "/" + base) : base;
    },
    sep: "/",
    delimiter: ":",
  };
  nodePath.posix = nodePath;

  function EventEmitter() {
    this._events = new Map();
    this._maxListeners = 10;
  }
  EventEmitter.prototype.setMaxListeners = function (n) { this._maxListeners = n; return this; };
  EventEmitter.prototype.on = function (e, fn) {
    if (!this._events.has(e)) this._events.set(e, []);
    this._events.get(e).push(fn);
    return this;
  };
  EventEmitter.prototype.addListener = EventEmitter.prototype.on;
  EventEmitter.prototype.once = function (e, fn) {
    const self = this;
    function wrapper() {
      self.off(e, wrapper);
      return fn.apply(this, arguments);
    }
    wrapper._orig = fn;
    return this.on(e, wrapper);
  };
  EventEmitter.prototype.off = function (e, fn) {
    const list = this._events.get(e);
    if (list) this._events.set(e, list.filter((x) => x !== fn && x._orig !== fn));
    return this;
  };
  EventEmitter.prototype.removeListener = EventEmitter.prototype.off;
  EventEmitter.prototype.removeAllListeners = function (e) {
    if (e === undefined) this._events.clear();
    else this._events.delete(e);
    return this;
  };
  EventEmitter.prototype.listeners = function (e) { return (this._events.get(e) || []).slice(); };
  EventEmitter.prototype.listenerCount = function (e) { return (this._events.get(e) || []).length; };
  EventEmitter.prototype.emit = function (e) {
    const list = this._events.get(e);
    if (!list || list.length === 0) return false;
    const args = Array.prototype.slice.call(arguments, 1);
    for (const fn of list.slice()) fn.apply(this, args);
    return true;
  };
  EventEmitter.EventEmitter = EventEmitter;

  function createHashLike(alg, hmacKey) {
    const chunks = [];
    return {
      update: function (chunk, enc) {
        if (chunk instanceof Uint8Array) chunks.push(chunk);
        else chunks.push(global.Buffer.from(String(chunk), enc));
        return this;
      },
      digest: function (enc) {
        const total = global.Buffer.concat(chunks);
        const b64 = total.toString("base64");
        const bytes = hmacKey !== undefined
          ? hostCallSync("crypto.hmacBytesBase64", { alg: alg, keyBase64: global.Buffer.from(hmacKey).toString("base64"), base64: b64 })
          : hostCallSync("crypto.hashBytesBase64", { alg: alg, base64: b64 });
        const buf = global.Buffer.from(Array.isArray(bytes) ? bytes : []);
        if (!enc) return buf;
        return buf.toString(enc);
      },
    };
  }

  const nodeFsSync = {
    existsSync: function (p) { return Boolean(hostCallSync("fs.exists", { path: String(p) })); },
    readFileSync: function (p, options) {
      const encoding = typeof options === "string" ? options : (options && options.encoding);
      if (encoding) {
        const res = hostCallSync("fs.read", { path: String(p) });
        if (res === null || res === undefined) throw makeFsError("ENOENT", p, "no such file or directory, open");
        if (encoding === "utf8" || encoding === "utf-8") return String(res);
        return global.Buffer.from(String(res), "utf8").toString(encoding);
      }
      const b64 = hostCallSync("fs.readBase64", { path: String(p) });
      if (b64 === null || b64 === undefined) throw makeFsError("ENOENT", p, "no such file or directory, open");
      return global.Buffer.from(String(b64), "base64");
    },
    writeFileSync: function (p, data) {
      const ok = data instanceof Uint8Array
        ? hostCallSync("fs.writeBase64", { path: String(p), base64: global.Buffer.from(data).toString("base64") })
        : hostCallSync("fs.write", { path: String(p), content: String(data) });
      if (!ok) throw makeFsError("EACCES", p, "permission denied or outside extension sandbox");
    },
    mkdirSync: function (p, options) {
      const ok = hostCallSync("fs.mkdir", { path: String(p), recursive: Boolean(options && options.recursive) });
      if (!ok) throw makeFsError("EACCES", p, "cannot create directory");
    },
    readdirSync: function (p) {
      const entries = hostCallSync("fs.readdir", { path: String(p) });
      if (!Array.isArray(entries)) throw makeFsError("ENOENT", p, "no such directory");
      return entries;
    },
    statSync: function (p) {
      const st = hostCallSync("fs.stat", { path: String(p) });
      if (!st) throw makeFsError("ENOENT", p, "no such file or directory, stat");
      return {
        size: st.size || 0,
        mtimeMs: st.mtimeMs || 0,
        mtime: new Date(st.mtimeMs || 0),
        isFile: function () { return Boolean(st.isFile); },
        isDirectory: function () { return Boolean(st.isDirectory); },
        isSymbolicLink: function () { return false; },
      };
    },
    unlinkSync: function (p) {
      if (!hostCallSync("fs.unlink", { path: String(p) })) throw makeFsError("ENOENT", p, "unlink failed");
    },
    rmSync: function (p, opts) {
      if (!hostCallSync("fs.rm", { path: String(p), recursive: Boolean(opts && opts.recursive) }) && !(opts && opts.force)) {
        throw makeFsError("ENOENT", p, "rm failed");
      }
    },
    renameSync: function (src, dest) {
      if (!hostCallSync("fs.rename", { src: String(src), dest: String(dest) })) throw makeFsError("EACCES", src, "rename failed");
    },
    copyFileSync: function (src, dest) {
      if (!hostCallSync("fs.copyFile", { src: String(src), dest: String(dest) })) throw makeFsError("EACCES", src, "copyFile failed");
    },
  };
  nodeFsSync.lstatSync = nodeFsSync.statSync;
  const nodeFsPromises = {
    readFile: function (p, opts) { return Promise.resolve().then(() => nodeFsSync.readFileSync(p, opts)); },
    writeFile: function (p, d) { return Promise.resolve().then(() => nodeFsSync.writeFileSync(p, d)); },
    mkdir: function (p, opts) { return Promise.resolve().then(() => nodeFsSync.mkdirSync(p, opts)); },
    readdir: function (p) { return Promise.resolve().then(() => nodeFsSync.readdirSync(p)); },
    stat: function (p) { return Promise.resolve().then(() => nodeFsSync.statSync(p)); },
    lstat: function (p) { return Promise.resolve().then(() => nodeFsSync.statSync(p)); },
    access: function (p) {
      return nodeFsSync.existsSync(p) ? Promise.resolve() : Promise.reject(makeFsError("ENOENT", p, "access"));
    },
    unlink: function (p) { return Promise.resolve().then(() => nodeFsSync.unlinkSync(p)); },
    rm: function (p, opts) { return Promise.resolve().then(() => nodeFsSync.rmSync(p, opts)); },
    rename: function (s, d) { return Promise.resolve().then(() => nodeFsSync.renameSync(s, d)); },
    copyFile: function (s, d) { return Promise.resolve().then(() => nodeFsSync.copyFileSync(s, d)); },
  };
  nodeFsSync.promises = nodeFsPromises;

  function StreamBase() { EventEmitter.call(this); }
  StreamBase.prototype = Object.create(EventEmitter.prototype);
  StreamBase.prototype.pipe = function (dest) {
    this.on("data", function (c) { if (dest && typeof dest.write === "function") dest.write(c); });
    this.on("end", function () { if (dest && typeof dest.end === "function") dest.end(); });
    return dest;
  };
  function Readable() { StreamBase.call(this); }
  Readable.prototype = Object.create(StreamBase.prototype);
  Readable.prototype.read = function () { return null; };
  Readable.prototype.resume = function () { return this; };
  Readable.prototype.pause = function () { return this; };
  Readable.from = function (iterable) {
    const r = new Readable();
    queueMicrotask(function () {
      for (const item of iterable) r.emit("data", item);
      r.emit("end");
    });
    return r;
  };
  function Writable() { StreamBase.call(this); }
  Writable.prototype = Object.create(StreamBase.prototype);
  Writable.prototype.write = function () { return true; };
  Writable.prototype.end = function () { this.emit("finish"); return this; };
  function PassThrough() { StreamBase.call(this); }
  PassThrough.prototype = Object.create(Writable.prototype);
  PassThrough.prototype.write = function (c) { this.emit("data", c); return true; };
  PassThrough.prototype.end = function (c) { if (c !== undefined) this.emit("data", c); this.emit("end"); this.emit("finish"); return this; };

  const nodeQuerystring = {
    parse: function (str) {
      const out = {};
      const params = new global.URLSearchParams(String(str || ""));
      for (const [k, v] of params.entries()) {
        if (Object.prototype.hasOwnProperty.call(out, k)) {
          out[k] = Array.isArray(out[k]) ? out[k].concat([v]) : [out[k], v];
        } else {
          out[k] = v;
        }
      }
      return out;
    },
    stringify: function (obj) {
      const p = new global.URLSearchParams();
      if (obj && typeof obj === "object") {
        for (const k of Object.keys(obj)) {
          const v = obj[k];
          if (Array.isArray(v)) v.forEach((x) => p.append(k, String(x)));
          else if (v !== undefined) p.append(k, String(v));
        }
      }
      return p.toString();
    },
    escape: encodeURIComponent,
    unescape: decodeURIComponent,
  };

  const nodeUtil = {
    promisify: function (fn) {
      return function () {
        const args = Array.prototype.slice.call(arguments);
        return new Promise(function (resolve, reject) {
          args.push(function (err, res) {
            if (err) reject(err);
            else resolve(res);
          });
          fn.apply(null, args);
        });
      };
    },
    inspect: function (v) {
      if (typeof v === "string") return "'" + v + "'";
      try { return JSON.stringify(v, null, 2); } catch (_) { return String(v); }
    },
    format: function (f) {
      const args = Array.prototype.slice.call(arguments, 1);
      if (typeof f !== "string") return [f].concat(args).map(String).join(" ");
      let i = 0;
      let out = f.replace(/%[sdjifoO%]/g, function (m) {
        if (m === "%%") return "%";
        if (i >= args.length) return m;
        const val = args[i++];
        if (m === "%j") { try { return JSON.stringify(val); } catch (_) { return String(val); } }
        return String(val);
      });
      while (i < args.length) out += " " + String(args[i++]);
      return out;
    },
    deprecate: function (fn) { return fn; },
    inherits: function (ctor, superCtor) {
      ctor.super_ = superCtor;
      ctor.prototype = Object.create(superCtor.prototype, { constructor: { value: ctor, enumerable: false, writable: true, configurable: true } });
    },
    TextEncoder: global.TextEncoder,
    TextDecoder: global.TextDecoder,
    types: {
      isUint8Array: function (v) { return v instanceof Uint8Array; },
      isArrayBuffer: function (v) { return v instanceof ArrayBuffer; },
      isDate: function (v) { return v instanceof Date; },
      isRegExp: function (v) { return v instanceof RegExp; },
      isPromise: function (v) { return v instanceof Promise; },
    },
  };

  const nodeChildProcess = {
    exec: function (cmd, opts, cb) {
      const callback = typeof opts === "function" ? opts : cb;
      const err = new Error("child_process.exec('" + String(cmd).split(" ")[0] + "') is not supported on Android.");
      if (typeof callback === "function") queueMicrotask(() => callback(err, "", err.message));
      return new EventEmitter();
    },
    execFile: function (file, args, opts, cb) {
      const callback = typeof args === "function" ? args : (typeof opts === "function" ? opts : cb);
      const err = new Error("child_process.execFile('" + file + "') is not supported on Android.");
      if (typeof callback === "function") queueMicrotask(() => callback(err, "", err.message));
      return new EventEmitter();
    },
    execSync: function (cmd) {
      throw new Error("child_process.execSync('" + String(cmd).split(" ")[0] + "') is not supported on Android.");
    },
    execFileSync: function (file) {
      throw new Error("child_process.execFileSync('" + file + "') is not supported on Android.");
    },
    spawn: function (cmd) {
      const ee = new EventEmitter();
      ee.stdout = new Readable();
      ee.stderr = new Readable();
      ee.stdin = new Writable();
      queueMicrotask(() => ee.emit("error", new Error("child_process.spawn('" + cmd + "') is not supported on Android.")));
      return ee;
    },
  };

  const nodeProcess = {
    env: {},
    argv: [],
    platform: "android",
    arch: "arm64",
    version: "v20.0.0",
    versions: { node: "20.0.0" },
    cwd: function () { return environment.supportPath || "/"; },
    nextTick: function (fn) {
      const args = Array.prototype.slice.call(arguments, 1);
      queueMicrotask(function () { fn.apply(null, args); });
    },
    hrtime: Object.assign(
      function (prev) {
        const ms = global.performance.now();
        const s = Math.floor(ms / 1000);
        const ns = Math.floor((ms % 1000) * 1e6);
        if (prev) return [s - prev[0], ns - prev[1]];
        return [s, ns];
      },
      { bigint: function () { return BigInt(Math.floor(global.performance.now() * 1e6)); } }
    ),
    exit: function (code) {
      if (code && code !== 0) reportRuntimeError(new Error("Process exited with code " + code), "process.exit", true);
      else closeMainWindow();
    },
    stdout: new Writable(),
    stderr: new Writable(),
  };

  const builtinModules = {
    react: React,
    "react/jsx-runtime": { jsx: jsx, jsxs: jsx, Fragment: FRAGMENT_TYPE },
    "react/jsx-dev-runtime": { jsxDEV: jsx, Fragment: FRAGMENT_TYPE },
    "react-dom": { version: "19.0.0" },
    "@raycast/api": raycastApi,
    "@vicinae/api": raycastApi,
    "@raycast/utils": raycastUtils,
    "node:crypto": {
      randomUUID: function () { return global.crypto.randomUUID(); },
      randomBytes: function (n) {
        const arr = hostCallSync("crypto.randomBytes", { length: n }) || [];
        return global.Buffer.from(arr);
      },
      createHash: function (alg) { return createHashLike(alg); },
      createHmac: function (alg, key) { return createHashLike(alg, key); },
      webcrypto: global.crypto,
    },
    "node:path": nodePath,
    "node:url": {
      URL: global.URL,
      URLSearchParams: global.URLSearchParams,
      pathToFileURL: function (p) { return new global.URL("file://" + normalizePath(p)); },
      fileURLToPath: function (u) { return (u instanceof global.URL ? u : new global.URL(u)).pathname; },
    },
    "node:buffer": { Buffer: global.Buffer },
    "node:timers": {
      setTimeout: global.setTimeout, clearTimeout: global.clearTimeout,
      setInterval: global.setInterval, clearInterval: global.clearInterval,
      setImmediate: global.setImmediate, clearImmediate: global.clearImmediate,
    },
    "node:timers/promises": {
      setTimeout: function (ms, val) { return new Promise((r) => global.setTimeout(() => r(val), ms)); },
    },
    "node:os": {
      platform: function () { return "android"; },
      type: function () { return "Linux"; },
      release: function () { return "6.1.0"; },
      arch: function () { return "arm64"; },
      endianness: function () { return "LE"; },
      EOL: "\n",
      cpus: function () { return [{ model: "ARM64", speed: 2000 }]; },
      homedir: function () { return environment.supportPath || "/data"; },
      tmpdir: function () { return (environment.supportPath || "/data") + "/tmp"; },
      userInfo: function () { return { username: "komorebi", homedir: environment.supportPath || "/data" }; },
      networkInterfaces: function () { return {}; },
    },
    "node:fs": nodeFsSync,
    "node:fs/promises": nodeFsPromises,
    "fs/promises": nodeFsPromises,
    "node:events": { EventEmitter: EventEmitter, default: EventEmitter },
    "node:process": nodeProcess,
    "node:util": nodeUtil,
    "node:querystring": nodeQuerystring,
    "node:stream": { Stream: StreamBase, Readable: Readable, Writable: Writable, Duplex: PassThrough, Transform: PassThrough, PassThrough: PassThrough },
    "node:child_process": nodeChildProcess,
    "node:perf_hooks": { performance: global.performance },
    "node:assert": Object.assign(
      function assert(cond, msg) { if (!cond) throw new Error(msg || "Assertion failed"); },
      {
        ok: function (cond, msg) { if (!cond) throw new Error(msg || "Assertion failed"); },
        strictEqual: function (a, b, msg) { if (!Object.is(a, b)) throw new Error(msg || ("Expected " + a + " === " + b)); },
        equal: function (a, b, msg) { if (a != b) throw new Error(msg || ("Expected " + a + " == " + b)); },
      }
    ),
    "node:module": {
      createRequire: function (filename) { return createRequire(dirname(String(filename || "/"))); },
      builtinModules: [],
    },
  };
  for (const k of Object.keys(builtinModules)) {
    if (k.startsWith("node:")) builtinModules[k.slice(5)] = builtinModules[k];
  }
  builtinModules["node:module"].builtinModules = Object.keys(builtinModules);
  global.process = nodeProcess;

  function readExtensionSource(relPath) {
    if (_fsMap[relPath] !== undefined) return _fsMap[relPath];
    return hostCallSync("fs.readExtensionFile", { path: relPath });
  }

  function compileCjsModule(code, filename) {
    try {
      return new Function("exports", "require", "module", "__filename", "__dirname", code);
    } catch (e) {
      let hint = "";
      if (/^\s*(import|export)\s/m.test(code)) {
        hint = " (File uses ES module 'import'/'export' syntax which is not compiled to CommonJS.)";
      } else if (/:\s*(string|number|boolean|React\.)/.test(code) || /<[A-Z][A-Za-z0-9.]*\s/.test(code)) {
        hint = " (File appears to contain uncompiled TypeScript/JSX syntax. Komorebi requires compiled .js bundles.)";
      }
      throw new SyntaxError("Failed to parse '" + filename + "': " + (e && e.message ? e.message : e) + hint);
    }
  }

  function resolveFileCandidates(base) {
    return [
      base,
      base + ".js",
      base + ".cjs",
      base + ".mjs",
      base + ".json",
      base + "/index.js",
      base + "/index.cjs",
      base + "/index.json",
    ];
  }

  function createRequire(baseDir) {
    return function require(id) {
      if (id in builtinModules) return builtinModules[id];

      const candidates = [];
      if (id.startsWith("./") || id.startsWith("../") || id.startsWith("/")) {
        const base = id.startsWith("/") ? normalizePath(id) : normalizePath(baseDir + "/" + id);
        candidates.push.apply(candidates, resolveFileCandidates(base));
      } else {
        // Walk up directories looking for node_modules/<id>
        let dir = normalizePath(baseDir || "/");
        while (true) {
          const nmBase = (dir === "/" ? "" : dir) + "/node_modules/" + id;
          const pkgRaw = readExtensionSource(nmBase + "/package.json");
          if (typeof pkgRaw === "string") {
            try {
              const pkg = JSON.parse(pkgRaw);
              if (pkg && pkg.main) {
                const mainTarget = normalizePath(nmBase + "/" + pkg.main);
                candidates.push.apply(candidates, resolveFileCandidates(mainTarget));
              }
            } catch (_) {}
          }
          candidates.push.apply(candidates, resolveFileCandidates(nmBase));
          if (dir === "/") break;
          dir = dirname(dir);
        }
      }

      for (const c of candidates) {
        if (c in _moduleCache) return _moduleCache[c].exports;
        const code = readExtensionSource(c);
        if (typeof code === "string") {
          const mod = { exports: {} };
          _moduleCache[c] = mod;
          if (c.endsWith(".json")) {
            try {
              mod.exports = JSON.parse(code);
              return mod.exports;
            } catch (e) {
              delete _moduleCache[c];
              throw new SyntaxError("Invalid JSON in '" + c + "': " + e.message);
            }
          }
          const fn = compileCjsModule(code, c);
          fn(mod.exports, createRequire(dirname(c)), mod, c, dirname(c));
          return mod.exports;
        }
      }
      throw new Error("Cannot find module '" + id + "' from '" + baseDir + "'. If this extension uses external npm dependencies, ensure it is compiled as a self-contained bundle.");
    };
  }

  // ---------------------------------------------------------------------------
  // Extension Launcher Entrypoint (called from Kotlin)
  // ---------------------------------------------------------------------------
  global.__launchCommand = function (configJson, entryCode) {
    const cfg = typeof configJson === "string" ? JSON.parse(configJson) : configJson;
    _preferences = cfg.preferences || {};
    Object.assign(environment, {
      commandName: cfg.commandName || "",
      commandMode: cfg.mode === "no-view" ? "no-view" : "view",
      extensionName: cfg.extensionName || "",
      ownerOrAuthorName: cfg.author || "",
      supportPath: cfg.supportPath || "",
      assetsPath: cfg.assetsPath || "",
      launchType: cfg.launchType || LaunchType.UserInitiated,
      appearance: cfg.appearance || "dark",
      theme: cfg.appearance || "dark",
    });

    if (cfg.files && typeof cfg.files === "object") {
      for (const k of Object.keys(cfg.files)) _fsMap[k] = cfg.files[k];
    }

    const entryPath = cfg.entryPath || "/index.js";
    const mod = { exports: {} };
    _moduleCache[entryPath] = mod;
    const fn = compileCjsModule(entryCode, entryPath);
    fn(mod.exports, createRequire(dirname(entryPath)), mod, entryPath, dirname(entryPath));

    const exported = mod.exports && (mod.exports.default || mod.exports);
    if (cfg.mode === "no-view") {
      return Promise.resolve(typeof exported === "function" ? exported({ arguments: cfg.arguments || {}, launchContext: cfg.launchContext || {} }) : null)
        .then(function () {
          hostCall("ui.noViewComplete", {});
          return { ok: true };
        })
        .catch(function (err) {
          reportRuntimeError(err, "no-view command", true);
          return { ok: false, error: formatError(err) };
        });
    }

    if (typeof exported !== "function") {
      throw new Error("Extension command '" + (cfg.commandName || entryPath) + "' did not export a default React component.");
    }
    const rootEl = createElement(NavigationProvider, {
      root: createElement(exported, { arguments: cfg.arguments || {}, launchContext: cfg.launchContext || {} }),
    });
    renderRoot(rootEl);
    return Promise.resolve({ ok: true });
  };

  global.__unmountExtension = function () {
    unmountRoot();
  };

  global.__getRaycastApi = function () { return raycastApi; };
  global.__React = React;
})(typeof globalThis !== "undefined" ? globalThis : this);
