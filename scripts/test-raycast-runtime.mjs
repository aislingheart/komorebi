#!/usr/bin/env node
/**
 * Automated Node.js regression suite for Komorebi's Raycast/Vicinae QuickJS runtime (`runtime.js`).
 * Loads `app/src/main/assets/raycast/runtime.js` in an isolated VM context with mocked native bridge
 * bindings and verifies React hooks, JSX tree reconciliation, @raycast/api, and @raycast/utils.
 */

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const RUNTIME_PATH = path.resolve("app/src/main/assets/raycast/runtime.js");
const runtimeSource = fs.readFileSync(RUNTIME_PATH, "utf8");

function createRuntimeHarness() {
  const renders = [];
  const logs = [];
  const hostCalls = [];
  const storage = new Map();
  let clipboardText = "initial-clip";

  const sandbox = {
    console: { log() {}, info() {}, warn() {}, error() {}, debug() {} },
    Math,
    Date,
    JSON,
    Object,
    Array,
    String,
    Number,
    Boolean,
    RegExp,
    Error,
    TypeError,
    RangeError,
    SyntaxError,
    Map,
    Set,
    WeakMap,
    WeakSet,
    Symbol,
    Promise,
    Uint8Array,
    Int8Array,
    Uint16Array,
    Int32Array,
    Float64Array,
    ArrayBuffer,
    DataView,
    parseInt,
    parseFloat,
    isNaN,
    isFinite,
    encodeURIComponent,
    decodeURIComponent,
    encodeURI,
    decodeURI,
    __nativeLog(level, msg) {
      logs.push({ level, msg });
    },
    __emitRender(treeJson) {
      renders.push(JSON.parse(treeJson));
    },
    async __scheduleTimeout(_id, ms) {
      await new Promise((r) => setTimeout(r, Math.min(Number(ms) || 0, 20)));
      return true;
    },
    async __nativeFetch(reqJson) {
      const req = JSON.parse(reqJson);
      hostCalls.push({ kind: "fetch", req });
      return JSON.stringify({
        status: 200,
        statusText: "OK",
        url: req.url,
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ ok: true, echo: req.url }),
        bodyBase64: Buffer.from(JSON.stringify({ ok: true, echo: req.url })).toString("base64"),
      });
    },
    __hostCallSync(method, payloadJson) {
      const payload = JSON.parse(payloadJson || "{}");
      hostCalls.push({ kind: "sync", method, payload });
      if (method === "crypto.randomUUID") return JSON.stringify("00000000-0000-4000-8000-000000000001");
      if (method === "fs.exists") return "false";
      if (method === "storage.setItem") {
        storage.set(payload.key, JSON.stringify(payload.value));
        return "true";
      }
      if (method === "storage.getItem") {
        return storage.has(payload.key) ? storage.get(payload.key) : null;
      }
      if (method === "storage.removeItem") {
        storage.delete(payload.key);
        return "true";
      }
      if (method === "storage.clear") {
        storage.clear();
        return "true";
      }
      if (method === "storage.allItems") {
        const out = {};
        for (const [k, v] of storage.entries()) out[k] = JSON.parse(v);
        return JSON.stringify(out);
      }
      return null;
    },
    async __hostCall(method, payloadJson) {
      const payload = JSON.parse(payloadJson || "{}");
      hostCalls.push({ kind: "async", method, payload });
      switch (method) {
        case "storage.setItem":
          storage.set(payload.key, JSON.stringify(payload.value));
          return "true";
        case "storage.getItem":
          return storage.has(payload.key) ? storage.get(payload.key) : null;
        case "storage.removeItem":
          storage.delete(payload.key);
          return "true";
        case "storage.clear":
          storage.clear();
          return "true";
        case "storage.allItems": {
          const out = {};
          for (const [k, v] of storage.entries()) out[k] = JSON.parse(v);
          return JSON.stringify(out);
        }
        case "clipboard.copy":
          clipboardText = payload.text || "";
          return "true";
        case "clipboard.readText":
          return JSON.stringify(clipboardText);
        case "ui.showToast":
        case "ui.showHUD":
          return "true";
        default:
          return null;
      }
    },
  };
  sandbox.globalThis = sandbox;
  sandbox.self = sandbox;
  sandbox.window = sandbox;

  const ctx = vm.createContext(sandbox);
  vm.runInContext(runtimeSource, ctx, { filename: "runtime.js" });
  return { ctx, sandbox, renders, logs, hostCalls, storage };
}

async function runTests() {
  let passed = 0;

  // Test 1: Boot runtime.js cleanly & expose global launch/callback entrypoints
  {
    const { sandbox } = createRuntimeHarness();
    assert.equal(typeof sandbox.__launchCommand, "function", "__launchCommand must be defined");
    assert.equal(typeof sandbox.__invokeCallback, "function", "__invokeCallback must be defined");
    assert.equal(typeof sandbox.__unmountExtension, "function", "__unmountExtension must be defined");
    passed++;
  }

  // Test 2: Launch a List command with React hooks (useState, useMemo, useEffect) + callback update
  {
    const { sandbox, renders } = createRuntimeHarness();
    const config = {
      commandName: "index",
      extensionName: "test-ext",
      author: "komorebi",
      mode: "view",
      supportPath: "/tmp/support",
      assetsPath: "/tmp/assets",
      appearance: "dark",
      preferences: { apiKey: "secret-123" },
      arguments: {},
      launchType: "User",
      entryPath: "/index.js",
    };
    const entryCode = `
      const React = require("react");
      const { List, ActionPanel, Action, getPreferenceValues } = require("@raycast/api");
      module.exports = function Command() {
        const [count, setCount] = React.useState(0);
        const prefs = getPreferenceValues();
        const label = React.useMemo(() => "Count: " + count + " (" + prefs.apiKey + ")", [count, prefs.apiKey]);
        return React.createElement(
          List,
          { navigationTitle: "Counter" },
          React.createElement(List.Item, {
            id: "item-1",
            title: label,
            actions: React.createElement(
              ActionPanel,
              null,
              React.createElement(Action, {
                title: "Increment",
                onAction: () => setCount((c) => c + 1),
              })
            ),
          })
        );
      };
    `;
    sandbox.__launchCommand(JSON.stringify(config), entryCode);
    await new Promise((r) => setTimeout(r, 30));

    assert.ok(renders.length >= 1, "Expected at least one render tree emission");
    const initialTree = JSON.stringify(renders[renders.length - 1]);
    assert.ok(initialTree.includes("Count: 0 (secret-123)"), "Initial render must include state + preference value");

    // Extract callback id for Increment action and invoke it
    const cbMatch = initialTree.match(/"onAction":"([^"]+)"/);
    assert.ok(cbMatch, "Expected serialized onAction callback ID");
    sandbox.__invokeCallback(cbMatch[1], "[]");
    await new Promise((r) => setTimeout(r, 30));

    const updatedTree = JSON.stringify(renders[renders.length - 1]);
    assert.ok(updatedTree.includes("Count: 1 (secret-123)"), "Re-render after callback must reflect updated state");
    sandbox.__unmountExtension();
    passed++;
  }

  // Test 3: LocalStorage, Clipboard, showToast & fetch bridge round-trip
  {
    const { sandbox, hostCalls } = createRuntimeHarness();
    const config = {
      commandName: "bg",
      extensionName: "bridge-test",
      author: "komorebi",
      mode: "no-view",
      supportPath: "/tmp/support",
      assetsPath: "/tmp/assets",
      appearance: "light",
      preferences: {},
      arguments: {},
      launchType: "User",
      entryPath: "/bg.js",
    };
    const entryCode = `
      const { LocalStorage, Clipboard, showToast } = require("@raycast/api");
      module.exports = async function Command() {
        await LocalStorage.setItem("k1", "hello-komorebi");
        const got = await LocalStorage.getItem("k1");
        await Clipboard.copy("copied:" + got);
        const clip = await Clipboard.readText();
        await showToast({ title: "Done", message: clip });
        const resp = await fetch("https://example.test/ping");
        const data = await resp.json();
        if (!data.ok) throw new Error("fetch failed");
      };
    `;
    sandbox.__launchCommand(JSON.stringify(config), entryCode);
    await new Promise((r) => setTimeout(r, 60));

    assert.ok(hostCalls.some((c) => c.method === "storage.setItem" && c.payload.key === "k1"), "LocalStorage.setItem called");
    assert.ok(hostCalls.some((c) => c.method === "clipboard.copy" && c.payload.text === "copied:hello-komorebi"), "Clipboard.copy round-tripped storage");
    assert.ok(hostCalls.some((c) => c.method === "ui.showToast" && c.payload.message === "copied:hello-komorebi"), "showToast called with clipboard value");
    assert.ok(hostCalls.some((c) => c.kind === "fetch" && c.req.url === "https://example.test/ping"), "fetch invoked native bridge");
    passed++;
  }

  // Test 4: @raycast/utils exports (usePromise / useCachedState / useFetch) exist and resolve
  {
    const { sandbox, renders } = createRuntimeHarness();
    const config = {
      commandName: "utils",
      extensionName: "utils-test",
      author: "komorebi",
      mode: "view",
      supportPath: "/tmp/support",
      assetsPath: "/tmp/assets",
      appearance: "dark",
      preferences: {},
      arguments: {},
      launchType: "User",
      entryPath: "/utils.js",
    };
    const entryCode = `
      const React = require("react");
      const { Detail } = require("@raycast/api");
      const { usePromise, useCachedState } = require("@raycast/utils");
      module.exports = function Command() {
        const [cached] = useCachedState("cached-k", "fallback-val");
        const { data } = usePromise(async () => "async-ok");
        return React.createElement(Detail, {
          markdown: (data || "loading") + "::" + cached,
        });
      };
    `;
    sandbox.__launchCommand(JSON.stringify(config), entryCode);
    await new Promise((r) => setTimeout(r, 60));
    const latest = JSON.stringify(renders[renders.length - 1]);
    assert.ok(latest.includes("async-ok::fallback-val"), "Expected @raycast/utils hooks to resolve and render");
    passed++;
  }

  console.log(`PASS: All ${passed} Raycast runtime.js test suites succeeded.`);
}

runTests().catch((err) => {
  console.error("FAIL: Raycast runtime.js test suite failed:", err);
  process.exit(1);
});
