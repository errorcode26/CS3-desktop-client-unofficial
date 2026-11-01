/**
 * Headless Node.js V8 execution test for player-ui (player.html + player.js).
 * Validates that:
 * 1. player.js boots in a WebView2-like DOM environment without uncaught exceptions.
 * 2. Strict storage restrictions (e.g. sandboxed localStorage throwing SecurityError) do not crash the script.
 * 3. The initial 'ui_ready' handshake is dispatched to the host bridge.
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

// Locate resources
function findResource(subpath) {
    const candidates = [
        path.resolve(__dirname, '../../main/resources/player-ui', subpath),
        path.resolve(process.cwd(), 'desktop-app/src/main/resources/player-ui', subpath),
        path.resolve(process.cwd(), 'src/main/resources/player-ui', subpath)
    ];
    for (const c of candidates) {
        if (fs.existsSync(c)) return c;
    }
    throw new Error(`Resource not found: ${subpath}`);
}

const htmlPath = findResource('player.html');
const jsPath = findResource('player.js');

const rawHtml = fs.readFileSync(htmlPath, 'utf8');
const rawJs = fs.readFileSync(jsPath, 'utf8');

// Parse declared DOM IDs from HTML
const idRegex = /id=["']([^"']+)["']/g;
const declaredIds = new Set();
let m;
while ((m = idRegex.exec(rawHtml)) !== null) {
    declaredIds.add(m[1]);
}

// Minimal Virtual DOM for V8 execution
const domElements = new Map();
function getOrCreateElement(id, tag = 'div') {
    if (domElements.has(id)) return domElements.get(id);
    const classes = new Set();
    const children = [];
    let text = '';
    const el = {
        tagName: tag.toUpperCase(),
        id: id || '',
        classList: {
            add: (...cls) => cls.forEach(c => c && classes.add(c)),
            remove: (...cls) => cls.forEach(c => classes.delete(c)),
            toggle: (c, force) => {
                if (force === true) classes.add(c);
                else if (force === false) classes.delete(c);
                else if (classes.has(c)) classes.delete(c);
                else classes.add(c);
            },
            contains: (c) => classes.has(c),
            toString: () => Array.from(classes).join(' ')
        },
        style: (() => {
            const s = {
                setProperty: (k, v) => { s[k] = String(v); },
                removeProperty: (k) => { delete s[k]; },
                getPropertyValue: (k) => s[k] || ''
            };
            return new Proxy(s, {
                get: (target, prop) => prop in target ? target[prop] : '',
                set: (target, prop, value) => { target[prop] = String(value); return true; }
            });
        })(),
        get innerText() { return text; },
        set innerText(v) { text = String(v); },
        get textContent() { return text; },
        set textContent(v) { text = String(v); },
        get innerHTML() { return text; },
        set innerHTML(v) { text = String(v); },
        value: '',
        dataset: {},
        children,
        childNodes: children,
        offsetWidth: 1920,
        offsetHeight: 1080,
        clientWidth: 1920,
        clientHeight: 1080,
        parentNode: null,
        setAttribute: (k, v) => {},
        getAttribute: (k) => null,
        hasAttribute: (k) => false,
        removeAttribute: (k) => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        appendChild: (child) => { children.push(child); return child; },
        removeChild: (child) => child,
        querySelector: () => null,
        querySelectorAll: () => [],
        closest: () => null,
        focus: () => {},
        blur: () => {}
    };
    if (id) domElements.set(id, el);
    return el;
}

// Seed all HTML declared IDs
declaredIds.forEach(id => getOrCreateElement(id));

const postMessagesSent = [];
const eventListeners = {};

// Sandbox environment simulating WebView2
const mockWindow = {
    console,
    Date,
    Math,
    JSON,
    Array,
    Object,
    String,
    Number,
    Boolean,
    RegExp,
    Set,
    Map,
    parseInt,
    parseFloat,
    isNaN,
    isFinite,
    document: {
        readyState: 'complete',
        title: 'CloudStream Player',
        body: getOrCreateElement('body', 'body'),
        documentElement: getOrCreateElement('html', 'html'),
        getElementById: (id) => {
            if (declaredIds.has(id)) return domElements.get(id);
            return null;
        },
        querySelector: (sel) => {
            if (sel.startsWith('#')) {
                const id = sel.substring(1);
                if (declaredIds.has(id)) return domElements.get(id);
                return null;
            }
            return getOrCreateElement('', sel.replace(/[^a-zA-Z0-9_-]/g, ''));
        },
        querySelectorAll: () => [],
        createElement: (tag) => getOrCreateElement('', tag),
        createTextNode: (t) => ({ textContent: t }),
        addEventListener: (event, fn) => {
            eventListeners[event] = eventListeners[event] || [];
            eventListeners[event].push(fn);
        },
        removeEventListener: () => {}
    },
    chrome: {
        webview: {
            postMessage: (msg) => postMessagesSent.push(msg),
            addEventListener: (event, fn) => {
                eventListeners[event] = eventListeners[event] || [];
                eventListeners[event].push(fn);
            }
        }
    },
    addEventListener: (event, fn) => {
        eventListeners[event] = eventListeners[event] || [];
        eventListeners[event].push(fn);
    },
    removeEventListener: () => {},
    setTimeout: (fn) => setTimeout(fn, 0),
    clearTimeout: (id) => clearTimeout(id),
    setInterval: () => 1,
    clearInterval: () => {},
    requestAnimationFrame: (fn) => setTimeout(fn, 0),
    cancelAnimationFrame: () => {},
    innerWidth: 1920,
    innerHeight: 1080,
    // Simulate strict Windows sandbox where localStorage throws SecurityError
    get localStorage() {
        throw new Error("SecurityError: Access is denied for this document.");
    }
};
mockWindow.window = mockWindow;

const context = vm.createContext(mockWindow);

// Execute player.js in V8
try {
    vm.runInContext(rawJs, context, { filename: 'player.js' });
} catch (err) {
    console.error('[FAIL] player.js threw fatal exception on boot:\n', err);
    process.exit(1);
}

// Verify 'ui_ready' handshake
const uiReady = postMessagesSent.find(m => m && m.type === 'ui_ready');
if (!uiReady) {
    console.error('[FAIL] player.js booted but failed to dispatch "ui_ready" to window.chrome.webview');
    process.exit(2);
}

console.log('[SUCCESS] player.js booted cleanly in V8 and dispatched "ui_ready"');

// Test inbound lifecycle messages
function deliverMessage(payload) {
    const list = eventListeners['message'] || [];
    list.forEach(fn => fn({ data: payload }));
}

try {
    deliverMessage({
        type: 'app_state_update',
        volume: 100,
        isMuted: false,
        isAppLoading: false,
        loadingStatusText: null
    });
    deliverMessage({
        value: {
            title: 'Hero (2022)',
            isProbing: false,
            links: [{ index: 0, name: '1080p', quality: 1080, url: 'http://test' }],
            episodes: [{ id: '1', name: 'Episode 1', season: 1, episode: 1, data: '1' }]
        }
    });
    deliverMessage({
        type: 'state_update',
        positionMs: 15000,
        durationMs: 7200000,
        isPlaying: true,
        isLoading: false
    });
    if (typeof mockWindow.__dismissProbingOverlay === 'function') {
        mockWindow.__dismissProbingOverlay();
    }
} catch (err) {
    console.error('[FAIL] player.js threw unhandled error during message lifecycle:\n', err);
    process.exit(3);
}

console.log('[SUCCESS] All inbound player messages handled without uncaught exceptions');
process.exit(0);
