// Embedded React + @raycast/api + @vicinae/api + @raycast/utils runtime for QuickJS
(function (global) {
  "use strict";

  // ---------------------------------------------------------------------------
  // Polyfills: console, timers, TextEncoder/TextDecoder, URL, URLSearchParams
  // ---------------------------------------------------------------------------
  if (typeof global.console === "undefined") {
    global.console = {};
  }
  const _origLog = global.console.log ? global.console.log.bind(global.console) : function () {};
  function formatArgs(args) {
    return Array.prototype.slice.call(args).map(function (a) {
      if (typeof a === "string") return a;
      try { return JSON.stringify(a); } catch (_) { return String(a); }
    }).join(" ");
  }
  global.console.log = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("LOG", formatArgs(arguments)); else _origLog.apply(null, arguments); };
  global.console.info = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("INFO", formatArgs(arguments)); };
  global.console.warn = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("WARN", formatArgs(arguments)); };
  global.console.error = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("ERROR", formatArgs(arguments)); };
  global.console.debug = function () { if (typeof global.__nativeLog === "function") global.__nativeLog("DEBUG", formatArgs(arguments)); };

  if (typeof global.queueMicrotask !== "function") {
    global.queueMicrotask = function (fn) {
      Promise.resolve().then(fn);
    };
  }

  let _timerSeq = 1;
  const _activeTimers = new Set();
  if (typeof global.setTimeout !== "function") {
    global.setTimeout = function (fn, ms) {
      const id = _timerSeq++;
      const args = Array.prototype.slice.call(arguments, 2);
      _activeTimers.add(id);
      if (typeof global.__scheduleTimeout === "function") {
        global.__scheduleTimeout(id, Math.max(0, Number(ms) || 0)).then(function () {
          if (_activeTimers.delete(id)) {
            try { fn.apply(null, args); } catch (e) { console.error("Timer error:", e && e.stack || e); }
          }
        });
      } else {
        Promise.resolve().then(function () {
          if (_activeTimers.delete(id)) fn.apply(null, args);
        });
      }
      return id;
    };
    global.clearTimeout = function (id) { _activeTimers.delete(id); };
  }
  if (typeof global.setInterval !== "function") {
    const _intervals = new Set();
    global.setInterval = function (fn, ms) {
      const id = _timerSeq++;
      const args = Array.prototype.slice.call(arguments, 2);
      const delay = Math.max(16, Number(ms) || 16);
      _intervals.add(id);
      function tick() {
        if (!_intervals.has(id)) return;
        global.setTimeout(function () {
          if (!_intervals.has(id)) return;
          try { fn.apply(null, args); } catch (e) { console.error("Interval error:", e); }
          tick();
        }, delay);
      }
      tick();
      return id;
    };
    global.clearInterval = function (id) { _intervals.delete(id); };
  }

  if (typeof global.Buffer === "undefined") {
    function _Buffer(arr) {
      return arr instanceof Uint8Array ? arr : new Uint8Array(arr || 0);
    }
    _Buffer.from = function (input, enc) {
      if (typeof input === "string") {
        if (enc === "base64") {
          const bin = global.atob(input);
          const out = new Uint8Array(bin.length);
          for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
          out.toString = _bufToString;
          return out;
        }
        if (enc === "hex") {
          const out = new Uint8Array(input.length / 2);
          for (let i = 0; i < out.length; i++) out[i] = parseInt(input.substr(i * 2, 2), 16);
          out.toString = _bufToString;
          return out;
        }
        const utf8 = unescape(encodeURIComponent(input));
        const out = new Uint8Array(utf8.length);
        for (let i = 0; i < utf8.length; i++) out[i] = utf8.charCodeAt(i);
        out.toString = _bufToString;
        return out;
      }
      const out = new Uint8Array(input || 0);
      out.toString = _bufToString;
      return out;
    };
    _Buffer.alloc = function (n) {
      const out = new Uint8Array(n);
      out.toString = _bufToString;
      return out;
    };
    _Buffer.isBuffer = function (x) { return x instanceof Uint8Array; };
    function _bufToString(enc) {
      if (enc === "base64") {
        let bin = "";
        for (let i = 0; i < this.length; i++) bin += String.fromCharCode(this[i]);
        return global.btoa(bin);
      }
      if (enc === "hex") {
        let hex = "";
        for (let i = 0; i < this.length; i++) hex += ("0" + this[i].toString(16)).slice(-2);
        return hex;
      }
      return new global.TextDecoder().decode(this);
    }
    global.Buffer = _Buffer;
  }

  if (typeof global.TextEncoder === "undefined") {
    global.TextEncoder = function TextEncoder() {};
    global.TextEncoder.prototype.encode = function (str) {
      const utf8 = unescape(encodeURIComponent(String(str)));
      const arr = new Uint8Array(utf8.length);
      for (let i = 0; i < utf8.length; i++) arr[i] = utf8.charCodeAt(i);
      return arr;
    };
  }
  if (typeof global.TextDecoder === "undefined") {
    global.TextDecoder = function TextDecoder() {};
    global.TextDecoder.prototype.decode = function (bytes) {
      if (!bytes) return "";
      const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
      let s = "";
      for (let i = 0; i < arr.length; i++) s += String.fromCharCode(arr[i]);
      try { return decodeURIComponent(escape(s)); } catch (_) { return s; }
    };
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
      } else if (init && typeof init === "object") {
        for (const k of Object.keys(init)) {
          this._entries.push([String(k), String(init[k])]);
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
    global.URLSearchParams.prototype.has = function (k) { return this._entries.some((e) => e[0] === String(k)); };
    global.URLSearchParams.prototype.delete = function (k) { this._entries = this._entries.filter((e) => e[0] !== String(k)); };
    global.URLSearchParams.prototype.toString = function () {
      return this._entries.map(([k, v]) => encodeURIComponent(k) + "=" + encodeURIComponent(v)).join("&");
    };
    global.URLSearchParams.prototype.entries = function () { return this._entries[Symbol.iterator](); };
    global.URLSearchParams.prototype[Symbol.iterator] = global.URLSearchParams.prototype.entries;
  }

  if (typeof global.URL === "undefined") {
    global.URL = function URL(input, base) {
      let raw = String(input);
      if (base && !/^[a-zA-Z][a-zA-Z\d+\-.]*:/.test(raw)) {
        const b = String(base);
        if (raw.startsWith("/")) {
          const m = b.match(/^([a-zA-Z][a-zA-Z\d+\-.]*:\/\/[^/]+)/);
          raw = (m ? m[1] : "") + raw;
        } else {
          raw = b.replace(/[^/]*$/, "") + raw;
        }
      }
      const m = raw.match(/^([a-zA-Z][a-zA-Z\d+\-.]*:)\/\/([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/);
      if (!m) {
        const schemeOnly = raw.match(/^([a-zA-Z][a-zA-Z\d+\-.]*:)(.*)$/);
        if (!schemeOnly) throw new TypeError("Invalid URL: " + input);
        this.protocol = schemeOnly[1];
        this.host = "";
        this.hostname = "";
        this.port = "";
        this.pathname = schemeOnly[2] || "";
        this.search = "";
        this.hash = "";
        this.origin = "null";
      } else {
        this.protocol = m[1];
        this.host = m[2] || "";
        const hp = this.host.split(":");
        this.hostname = hp[0] || "";
        this.port = hp[1] || "";
        this.pathname = m[3] || "/";
        this.search = m[4] || "";
        this.hash = m[5] || "";
        this.origin = this.protocol + "//" + this.host;
      }
      this.searchParams = new global.URLSearchParams(this.search);
    };
    global.URL.prototype.toString = function () {
      const qs = this.searchParams.toString();
      const s = qs ? "?" + qs : "";
      const authority = this.host ? "//" + this.host : "";
      return this.protocol + authority + this.pathname + s + this.hash;
    };
    global.URL.prototype.toJSON = global.URL.prototype.toString;
  }

  if (typeof global.AbortController === "undefined") {
    global.AbortController = function AbortController() {
      this.signal = { aborted: false, addEventListener: function () {}, removeEventListener: function () {} };
      this.abort = () => { this.signal.aborted = true; };
    };
  }

  if (typeof global.atob !== "function") {
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    global.btoa = function (input) {
      const str = String(input);
      let out = "";
      for (let i = 0; i < str.length; i += 3) {
        const a = str.charCodeAt(i);
        const b = i + 1 < str.length ? str.charCodeAt(i + 1) : 0;
        const c = i + 2 < str.length ? str.charCodeAt(i + 2) : 0;
        const triple = (a << 16) | (b << 8) | c;
        out += chars[(triple >> 18) & 63];
        out += chars[(triple >> 12) & 63];
        out += i + 1 < str.length ? chars[(triple >> 6) & 63] : "=";
        out += i + 2 < str.length ? chars[triple & 63] : "=";
      }
      return out;
    };
    global.atob = function (input) {
      const str = String(input).replace(/=+$/, "");
      let out = "";
      for (let i = 0; i < str.length; i += 4) {
        const e1 = chars.indexOf(str[i]);
        const e2 = chars.indexOf(str[i + 1]);
        const e3 = chars.indexOf(str[i + 2]);
        const e4 = chars.indexOf(str[i + 3]);
        const n = (e1 << 18) | (e2 << 12) | ((e3 & 63) << 6) | (e4 & 63);
        out += String.fromCharCode((n >> 16) & 255);
        if (e3 >= 0 && str[i + 2] !== undefined) out += String.fromCharCode((n >> 8) & 255);
        if (e4 >= 0 && str[i + 3] !== undefined) out += String.fromCharCode(n & 255);
      }
      return out;
    };
  }

  if (typeof global.fetch !== "function") {
    global.fetch = function (input, init) {
      const url = typeof input === "string" ? input : (input && input.url ? input.url : String(input));
      const opts = init || {};
      const method = (opts.method || "GET").toUpperCase();
      const headers = {};
      if (opts.headers) {
        if (typeof opts.headers.forEach === "function") {
          opts.headers.forEach((v, k) => { headers[k] = v; });
        } else {
          Object.assign(headers, opts.headers);
        }
      }
      const body = opts.body != null ? String(opts.body) : null;
      return global.__nativeFetch(JSON.stringify({ url, method, headers, body })).then(function (rawRes) {
        const res = typeof rawRes === "string" ? JSON.parse(rawRes) : rawRes;
        if (res.error) throw new Error(res.error);
        const respHeaders = res.headers || {};
        const headerObj = {
          get: function (k) {
            const lk = String(k).toLowerCase();
            for (const key of Object.keys(respHeaders)) {
              if (key.toLowerCase() === lk) return respHeaders[key];
            }
            return null;
          },
          has: function (k) { return headerObj.get(k) !== null; },
          forEach: function (cb) { for (const k of Object.keys(respHeaders)) cb(respHeaders[k], k); },
        };
        const textBody = res.body || "";
        return {
          ok: res.status >= 200 && res.status < 300,
          status: res.status,
          statusText: res.statusText || "",
          url: res.url || url,
          headers: headerObj,
          text: function () { return Promise.resolve(textBody); },
          json: function () { return Promise.resolve(JSON.parse(textBody)); },
          arrayBuffer: function () { return Promise.resolve(new TextEncoder().encode(textBody).buffer); },
        };
      });
    };
  }

  // ---------------------------------------------------------------------------
  // Host Bridge & Callback Manager (mirrors vicinae callback.ts & RpcTransport)
  // ---------------------------------------------------------------------------
  let _cbSeq = 1;
  const _callbacks = new Map();
  const _eventListeners = new Map();

  function registerCallback(fn) {
    const id = "cb_" + (_cbSeq++);
    _callbacks.set(id, fn);
    return id;
  }

  global.__invokeCallback = function (handlerId, argsJson) {
    const fn = _callbacks.get(handlerId);
    if (!fn) return null;
    const args = argsJson ? JSON.parse(argsJson) : [];
    const result = fn.apply(null, args);
    return result === undefined ? null : JSON.stringify(result);
  };

  global.__dispatchHostEvent = function (eventName, payloadJson) {
    const listeners = _eventListeners.get(eventName);
    if (!listeners) return;
    const payload = payloadJson ? JSON.parse(payloadJson) : {};
    for (const fn of Array.from(listeners)) {
      try { fn(payload); } catch (e) { console.error("Event listener error:", e && e.stack || e); }
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

  // ---------------------------------------------------------------------------
  // Lightweight React 18/19 + Reconciler Engine (mirrors vicinae reconciler.ts)
  // ---------------------------------------------------------------------------
  const REACT_ELEMENT_TYPE = Symbol.for("react.transitional.element");
  const FRAGMENT_TYPE = Symbol.for("react.fragment");
  const CONTEXT_TYPE = Symbol.for("react.context");

  let _currentFiber = null;
  let _hookIndex = 0;
  let _rootElement = null;
  let _rootInstanceTree = null;
  let _renderScheduled = false;
  const _pendingEffects = [];

  function createElement(type, props) {
    const outProps = {};
    let key = null;
    if (props != null) {
      if (props.key !== undefined) key = String(props.key);
      for (const k of Object.keys(props)) {
        if (k !== "key" && k !== "ref") outProps[k] = props[k];
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
        if (k !== "key" && k !== "ref") outProps[k] = props[k];
      }
    }
    return {
      $$typeof: REACT_ELEMENT_TYPE,
      type: type,
      key: key !== undefined ? String(key) : (props && props.key !== undefined ? String(props.key) : null),
      props: outProps,
    };
  }

  function isValidElement(obj) {
    return typeof obj === "object" && obj !== null && (obj.$$typeof === REACT_ELEMENT_TYPE || (obj.type !== undefined && obj.props !== undefined));
  }

  function cloneElement(element, config) {
    const newProps = Object.assign({}, element.props, config);
    const childrenLen = arguments.length - 2;
    if (childrenLen === 1) newProps.children = arguments[2];
    else if (childrenLen > 1) newProps.children = Array.prototype.slice.call(arguments, 2);
    return {
      $$typeof: REACT_ELEMENT_TYPE,
      type: element.type,
      key: config && config.key !== undefined ? String(config.key) : element.key,
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
    if (_renderScheduled) return;
    _renderScheduled = true;
    queueMicrotask(function () {
      _renderScheduled = false;
      commitRoot();
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
        rendered = type._render(props, null);
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
          console.error("useEffect error:", e && e.stack || e);
        }
      }
    }
  }

  function commitRoot() {
    if (!_rootElement) return;
    _callbacks.clear();
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
    _rootElement = element;
    commitRoot();
  }

  function unmountRoot() {
    _rootElement = null;
    _seenFiberKeys = new Set();
    cleanupUnusedFibers();
    _callbacks.clear();
  }

  const React = {
    Fragment: FRAGMENT_TYPE,
    createElement: createElement,
    cloneElement: cloneElement,
    isValidElement: isValidElement,
    createContext: createContext,
    Children: Children,
    useState: useState,
    useReducer: useReducer,
    useEffect: useEffect,
    useLayoutEffect: useEffect,
    useMemo: useMemo,
    useCallback: useCallback,
    useRef: useRef,
    useContext: useContext,
    forwardRef: function (render) { return { _render: render }; },
    memo: function (comp) { return comp; },
    version: "19.0.0",
  };

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
      if (typeof prop === "string") return prop.toLowerCase();
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
      if (img.fileIcon) return { fileIcon: img.fileIcon };
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
    return hostCall("ui.confirmAlert", {
      title: options.title || "",
      message: options.message || "",
      primaryAction: options.primaryAction ? { title: options.primaryAction.title, style: options.primaryAction.style || "Default" } : { title: "Confirm", style: "Default" },
      dismissAction: options.dismissAction ? { title: options.dismissAction.title, style: options.dismissAction.style || "Cancel" } : { title: "Cancel", style: "Cancel" },
    }).then(function (confirmed) {
      if (confirmed && options.primaryAction && typeof options.primaryAction.onAction === "function") {
        options.primaryAction.onAction();
      } else if (!confirmed && options.dismissAction && typeof options.dismissAction.onAction === "function") {
        options.dismissAction.onAction();
      }
      return Boolean(confirmed);
    });
  }

  const Clipboard = {
    copy: function (content, options) {
      const text = typeof content === "string" ? content : (content && (content.text || content.html || content.file || ""));
      return hostCall("clipboard.copy", { text: String(text), concealed: Boolean(options && options.concealed) });
    },
    paste: function (content) {
      const text = typeof content === "string" ? content : (content && (content.text || content.html || ""));
      return hostCall("clipboard.paste", { text: String(text) });
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
    return hostCall("system.open", { target: String(target), application: application ? String(application) : null });
  }
  function openExtensionPreferences() { return hostCall("ui.openPreferences", { scope: "extension" }); }
  function openCommandPreferences() { return hostCall("ui.openPreferences", { scope: "command" }); }
  function popToRoot(opts) { return hostCall("ui.popToRoot", { clearSearchBar: Boolean(opts && opts.clearSearchBar) }); }
  function clearSearchBar() { return hostCall("ui.clearSearchBar", {}); }
  function closeMainWindow() { return hostCall("ui.closeMainWindow", {}); }
  function updateCommandMetadata(meta) { return hostCall("ui.updateCommandMetadata", meta || {}); }
  function getApplications() { return hostCall("system.getApplications", {}).then((r) => r || []); }
  function getDefaultApplication(target) { return hostCall("system.getDefaultApplication", { target: String(target) }); }
  function getFrontmostApplication() { return Promise.resolve({ name: "Komorebi", bundleId: "com.aislingheart.komorebi" }); }
  function getSelectedText() { return Promise.resolve(""); }
  function showInFinder(path) { return open(path); }
  function showInFileBrowser(path) { return open(path); }
  function trash(path) { return hostCall("system.trash", { paths: Array.isArray(path) ? path : [path] }); }
  function launchCommand(opts) { return hostCall("system.launchCommand", opts || {}); }
  function captureException(err) { console.error("captureException:", err); }

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
      const redirectURI = (opts.extraParameters && opts.extraParameters.redirect_uri) || "https://raycast.com/redirect?packageName=Extension";
      return {
        codeChallenge,
        codeVerifier,
        state,
        redirectURI,
        toURL: function () {
          const url = new URL(opts.endpoint);
          url.searchParams.set("response_type", "code");
          url.searchParams.set("client_id", opts.clientId);
          url.searchParams.set("redirect_uri", redirectURI);
          url.searchParams.set("scope", opts.scope || "");
          url.searchParams.set("state", state);
          url.searchParams.set("code_challenge", codeChallenge);
          url.searchParams.set("code_challenge_method", "S256");
          if (opts.extraParameters) {
            for (const k of Object.keys(opts.extraParameters)) {
              url.searchParams.set(k, opts.extraParameters[k]);
            }
          }
          return url.toString();
        },
      };
    }
    async authorize(req) {
      const url = req.url || (typeof req.toURL === "function" ? req.toURL() : "");
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
    raycastVersion: "1.0.0",
    isRaycast: true,
    launchType: LaunchType.UserInitiated,
    canAccess: function () { return false; },
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
      const sub = subscribeEvent("ui.viewPopped", function () {
        setStack((prev) => (prev.length > 1 ? prev.slice(0, -1) : prev));
      });
      return function () { sub.unsubscribe(); };
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
      text: props.text,
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
        return createElement("list-item-detail-metadata-label", {
          title: props.title,
          text: typeof props.text === "object" && props.text ? props.text.value : props.text,
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
    ShowInFinder: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Open File",
        icon: props.icon || Icon.Finder,
        shortcut: props.shortcut,
        onAction: function () { open(props.path); },
      });
    },
    SubmitForm: function (props) {
      return createElement("action", {
        title: props.title || "Submit",
        icon: serializeImageLike(props.icon || Icon.Check),
        style: props.style || "regular",
        isSubmit: true,
        onSubmit: props.onSubmit,
        onAction: props.onSubmit,
      });
    },
    CreateQuicklink: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Copy Quicklink",
        icon: props.icon || Icon.Link,
        onAction: function () {
          if (props.quicklink && props.quicklink.link) Clipboard.copy(props.quicklink.link);
        },
      });
    },
    ToggleQuickLook: function (props) {
      return createElement(BaseAction, {
        title: props.title || "Quick Look",
        icon: props.icon || Icon.Eye,
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
        filtering: props.filtering,
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
      return {
        text: typeof acc.text === "string" ? { value: acc.text } : acc.text,
        tag: acc.tag ? (typeof acc.tag === "string" ? { value: acc.tag } : { value: acc.tag.value, color: serializeColorLike(acc.tag.color) }) : undefined,
        icon: serializeImageLike(acc.icon),
        date: acc.date ? String(acc.date) : undefined,
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
        title: typeof props.title === "string" ? props.title : (props.title && props.title.value) || "",
        subtitle: typeof props.subtitle === "string" ? props.subtitle : (props.subtitle && props.subtitle.value),
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
      return createElement("list", {
        isLoading: props.isLoading,
        isShowingDetail: props.isShowingDetail,
        searchText: props.searchText,
        searchBarPlaceholder: props.searchBarPlaceholder,
        navigationTitle: props.navigationTitle,
        filtering: props.filtering !== undefined ? props.filtering : (props.enableFiltering !== undefined ? props.enableFiltering : !props.onSearchTextChange),
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
      return createElement("grid", {
        columns: props.columns,
        aspectRatio: props.aspectRatio || "1",
        fit: props.fit || "contain",
        inset: props.inset,
        isLoading: props.isLoading,
        searchText: props.searchText,
        searchBarPlaceholder: props.searchBarPlaceholder,
        navigationTitle: props.navigationTitle,
        filtering: props.filtering !== undefined ? props.filtering : !props.onSearchTextChange,
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

  function formFieldProps(props, extra) {
    return Object.assign({
      id: props.id,
      title: props.title,
      info: props.info,
      error: props.error,
      value: props.value,
      defaultValue: props.defaultValue,
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
        { Type: { Date: "date", DateTime: "dateTime" } }
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
      FilePicker: function (props) { return createElement("form-text-field", formFieldProps(props, { placeholder: "File path..." })); },
      Separator: function () { return createElement("form-separator", {}); },
      Description: function (props) { return createElement("form-description", { title: props.title, text: props.text }); },
      LinkAccessory: function (props) { return createElement("form-description", { title: props.text, text: props.target }); },
    }
  );

  const raycastApi = {
    Action, ActionPanel, Alert, Cache, Color, Detail, Dropdown, Environment: environment, environment,
    Form, Grid, Icon, Image, Keyboard, LaunchType, List, LocalStorage, OAuth, PopToRootType, Toast,
    captureException, clearSearchBar, closeMainWindow, confirmAlert, getApplications, getDefaultApplication,
    getFrontmostApplication, getPreferenceValues, getSelectedText, launchCommand, open,
    openCommandPreferences, openExtensionPreferences, popToRoot, showHUD, showInFileBrowser,
    showInFinder, showToast, trash, updateCommandMetadata, useNavigation,
  };

  // @raycast/utils compatibility helpers
  function usePromise(fn, args, options) {
    const opts = options || {};
    const execute = opts.execute !== undefined ? opts.execute : true;
    const [state, setState] = useState({ data: opts.initialData, isLoading: execute, error: undefined });
    const runIdRef = useRef(0);
    const deps = Array.isArray(args) ? args : [];

    const revalidate = useCallback(function () {
      const id = ++runIdRef.current;
      setState((prev) => ({ data: prev.data, isLoading: true, error: undefined }));
      return Promise.resolve().then(() => fn.apply(null, deps)).then(
        function (data) {
          if (id === runIdRef.current) {
            setState({ data, isLoading: false, error: undefined });
            if (opts.onData) opts.onData(data);
          }
          return data;
        },
        function (error) {
          if (id === runIdRef.current) {
            setState((prev) => ({ data: prev.data, isLoading: false, error }));
            if (opts.onError) opts.onError(error);
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
        if (mOpts && mOpts.optimisticUpdate) {
          setState((prev) => ({ data: mOpts.optimisticUpdate(prev.data), isLoading: prev.isLoading, error: prev.error }));
        }
        return Promise.resolve(asyncUpdate).then(() => revalidate());
      },
    };
  }

  function useFetch(url, options) {
    const opts = options || {};
    return usePromise(
      function (u) {
        return global.fetch(u, opts).then(function (r) {
          if (!r.ok) throw new Error("HTTP " + r.status + " " + r.statusText);
          if (opts.parseResponse) return opts.parseResponse(r);
          return r.json();
        });
      },
      [url],
      opts
    );
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
    showFailureToast,
    getFavicon: function (url) {
      try {
        const u = new URL(url);
        return "https://www.google.com/s2/favicons?sz=64&domain=" + encodeURIComponent(u.hostname);
      } catch (_) {
        return Icon.Globe;
      }
    },
    getAvatarIcon: function (name) {
      return Icon.PersonCircle;
    },
  };

  // ---------------------------------------------------------------------------
  // Module Loader / CommonJS require() with Node core polyfills
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

  const builtinModules = {
    react: React,
    "react/jsx-runtime": { jsx: jsx, jsxs: jsx, Fragment: FRAGMENT_TYPE },
    "react/jsx-dev-runtime": { jsxDEV: jsx, Fragment: FRAGMENT_TYPE },
    "@raycast/api": raycastApi,
    "@vicinae/api": raycastApi,
    "@raycast/utils": raycastUtils,
    "node:crypto": {
      randomUUID: function () { return hostCallSync("crypto.randomUuid", {}) || ("uuid-" + Date.now() + "-" + Math.random()); },
      randomBytes: function (n) {
        const arr = hostCallSync("crypto.randomBytes", { length: n }) || [];
        return Uint8Array.from(arr);
      },
      createHash: function (alg) {
        let data = "";
        return {
          update: function (chunk) { data += String(chunk); return this; },
          digest: function (enc) {
            const hex = hostCallSync("crypto.hash", { alg: alg, input: data }) || "";
            if (enc === "hex") return hex;
            return hex;
          },
        };
      },
    },
    "node:path": {
      join: function () { return normalizePath(Array.prototype.join.call(arguments, "/")); },
      resolve: function () { return normalizePath(Array.prototype.join.call(arguments, "/")); },
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
      sep: "/",
    },
    "node:url": { URL: global.URL, URLSearchParams: global.URLSearchParams },
    "node:buffer": { Buffer: global.Buffer },
    "node:timers": { setTimeout: global.setTimeout, clearTimeout: global.clearTimeout, setInterval: global.setInterval, clearInterval: global.clearInterval },
    "node:os": { platform: function () { return "android"; }, homedir: function () { return environment.supportPath || "/data"; }, tmpdir: function () { return "/tmp"; } },
    "node:fs": {
      existsSync: function (p) { return Boolean(hostCallSync("fs.exists", { path: String(p) })); },
      readFileSync: function (p) { return hostCallSync("fs.read", { path: String(p) }) || ""; },
      writeFileSync: function (p, d) { return hostCallSync("fs.write", { path: String(p), content: String(d) }); },
      mkdirSync: function (p) { return hostCallSync("fs.mkdir", { path: String(p) }); },
    },
    "node:events": {
      EventEmitter: function EventEmitter() {
        const map = new Map();
        this.on = function (e, fn) { if (!map.has(e)) map.set(e, []); map.get(e).push(fn); return this; };
        this.off = function (e, fn) { const l = map.get(e); if (l) map.set(e, l.filter((x) => x !== fn)); return this; };
        this.emit = function (e) {
          const args = Array.prototype.slice.call(arguments, 1);
          for (const fn of (map.get(e) || [])) fn.apply(this, args);
          return true;
        };
      },
    },
    "node:process": { env: {}, argv: [], platform: "android", version: "v20.0.0" },
  };
  for (const k of Object.keys(builtinModules)) {
    if (k.startsWith("node:")) builtinModules[k.slice(5)] = builtinModules[k];
  }
  global.process = builtinModules["node:process"];

  function createRequire(baseDir) {
    return function require(id) {
      if (id in builtinModules) return builtinModules[id];
      const candidates = [id];
      if (id.startsWith("./") || id.startsWith("../") || id.startsWith("/")) {
        const base = id.startsWith("/") ? normalizePath(id) : normalizePath(baseDir + "/" + id);
        candidates.length = 0;
        candidates.push(base, base + ".js", base + ".json", base + "/index.js");
      }
      for (const c of candidates) {
        if (c in _moduleCache) return _moduleCache[c].exports;
        const code = _fsMap[c] !== undefined ? _fsMap[c] : hostCallSync("fs.readExtensionFile", { path: c });
        if (typeof code === "string" && code.length > 0) {
          const mod = { exports: {} };
          _moduleCache[c] = mod;
          if (c.endsWith(".json")) {
            mod.exports = JSON.parse(code);
            return mod.exports;
          }
          const fn = new Function("exports", "require", "module", "__filename", "__dirname", code);
          fn(mod.exports, createRequire(dirname(c)), mod, c, dirname(c));
          return mod.exports;
        }
      }
      throw new Error("Cannot find module '" + id + "' from '" + baseDir + "'");
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
    const fn = new Function("exports", "require", "module", "__filename", "__dirname", entryCode);
    fn(mod.exports, createRequire(dirname(entryPath)), mod, entryPath, dirname(entryPath));

    const exported = mod.exports && (mod.exports.default || mod.exports);
    if (cfg.mode === "no-view") {
      return Promise.resolve(typeof exported === "function" ? exported({ arguments: cfg.arguments || {} }) : null)
        .then(function () {
          hostCall("ui.noViewComplete", {});
          return { ok: true };
        });
    }

    if (typeof exported !== "function") {
      throw new Error("Extension command did not export a default React component.");
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
