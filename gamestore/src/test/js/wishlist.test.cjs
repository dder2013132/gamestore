const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/js/wishlist.js', 'utf8');

// Minimal browser boundary: exercise the shipped event handler with controlled requests.
function harness({ ids = ['12', '12', '12', '13'], saved = false, list = false } = {}) {
  const listeners = {}, calls = [], deferred = [], timers = new Map();
  let timerId = 0;
  const classes = () => ({ values: new Set(), toggle(name, enabled) { enabled ? this.values.add(name) : this.values.delete(name); } });
  const node = () => ({ attrs: {}, classList: classes(), hidden: true, isConnected: true,
    setAttribute(k, v) { this.attrs[k] = v; }, removeAttribute(k) { delete this.attrs[k]; },
    addEventListener() {}, focus() { doc.activeElement = this; } });
  const message = node(), recovery = node(), notice = node(), close = node();
  notice.querySelector = key => ({ '[data-wishlist-message]': message, '[data-wishlist-recovery]': recovery, '[data-wishlist-dismiss]': close }[key]);
  const makeForm = (id, initial) => {
    const svg = node(), button = node(), form = node();
    button.querySelector = () => svg;
    form.dataset = { gameId: id, saved: String(initial), title: 'Game <title>', addUrl: '/gamestore/wishlist/add', removeUrl: '/gamestore/wishlist/remove', loginUrl: '/gamestore/login' };
    form.fields = [['csrfToken', 'session-token'], ['gameId', id], ['returnTo', '/browse?page=2']];
    form.matches = selector => selector === '[data-wishlist-form]';
    form.querySelector = () => button;
    form.contains = element => element === button;
    form.button = button; form.svg = svg;
    return form;
  };
  let currentForms = ids.map(id => makeForm(id, saved));
  const label = { dataset: { gameId: ids[0] }, textContent: '' };
  const doc = { body: {}, activeElement: {}, addEventListener(name, callback) { listeners[name] = callback; },
    querySelector(selector) { return selector === '[data-wishlist-notice]' ? notice : root; },
    querySelectorAll(selector) { return selector === '[data-wishlist-form]' ? currentForms : [label]; },
    importNode(next) { return next; } };
  const makeRoot = (page, forms) => ({ dataset: { page: String(page), wishlistSession: 'session-token' }, forms,
    replaceWith(next) { currentForms.forEach(form => { form.isConnected = false; form.button.isConnected = false; }); root = next; currentForms = next.forms; },
    querySelector() { return this.forms[0]?.button || null; } });
  let root = list ? makeRoot(2, currentForms) : null;
  let nextRoot;
  const location = { href: list ? 'http://localhost/gamestore/wishlist?page=2#games' : 'http://localhost/gamestore/browse?page=2' };
  const history = { changes: [], replaceState(_state, _title, url) { this.changes.push(String(url)); location.href = String(url); } };
  const fetch = (url, options) => {
    calls.push({ url: String(url), options });
    return new Promise((resolve, reject) => { deferred.push({ resolve, reject }); });
  };
  class FormData { constructor(form) { return form.fields[Symbol.iterator](); } }
  class DOMParser { parseFromString() { return { querySelector() { return nextRoot; } }; } }
  const window = { fetch, FormData, AbortController, DOMParser, location, history };
  vm.runInNewContext(source, { document: doc, window, fetch, FormData, DOMParser, AbortController, URLSearchParams, URL,
    setTimeout(callback, delay) { timers.set(++timerId, { callback, delay }); return timerId; }, clearTimeout(id) { timers.delete(id); } });
  listeners.DOMContentLoaded();
  const response = (body, status = 200, type = 'application/json') => ({ ok: status < 400, status,
    headers: { get: () => type }, json: async () => body, text: async () => 'server-rendered fragment' });
  return { calls, deferred, response, notice, recovery, message, label, doc, history, location, timers,
    get forms() { return currentForms; }, get root() { return root; },
    submit(form = currentForms[0]) { return listeners.submit({ target: form, preventDefault() {} }); },
    nextPage(page, ids, token = 'session-token') { nextRoot = makeRoot(page, ids.map(id => makeForm(id, true))); nextRoot.dataset.wishlistSession = token; return nextRoot; } };
}
const tick = () => new Promise(resolve => setImmediate(resolve));

test('optimistic hearts synchronize, prevent duplicate requests, and preserve CSRF', async () => {
  const h = harness(), pending = h.submit();
  assert.equal(h.calls.length, 1);
  h.forms.slice(0, 3).forEach(form => {
    assert.equal(form.dataset.saved, 'true'); assert.equal(form.button.disabled, true);
    assert.equal(form.button.attrs['aria-pressed'], 'true'); assert.equal(form.svg.attrs.fill, 'currentColor');
  });
  assert.equal(h.forms[3].button.disabled, false);
  assert.equal(h.calls[0].options.body.get('csrfToken'), 'session-token');
  assert.equal(h.calls[0].options.credentials, 'same-origin');
  assert.equal(h.calls[0].options.headers.Accept, 'application/json');
  await h.submit(h.forms[1]);
  assert.equal(h.calls.length, 1);
  h.deferred[0].resolve(h.response({ gameId: 12, saved: true })); await pending;
  h.forms.slice(0, 3).forEach(form => { assert.equal(form.button.disabled, false); assert.equal(form.action, '/gamestore/wishlist/remove'); });
  assert.equal(h.label.textContent, 'お気に入りに保存済み');
  assert.equal(h.notice.hidden, false);
});

test('network failure restores all hearts and permits retry', async () => {
  const h = harness({ saved: true }), pending = h.submit();
  assert.equal(h.forms[0].dataset.saved, 'false');
  h.deferred[0].reject(new TypeError('Network lost')); await pending;
  h.forms.slice(0, 3).forEach(form => { assert.equal(form.dataset.saved, 'true'); assert.equal(form.button.disabled, false); });
  assert.equal(h.notice.classList.values.has('is-error'), true);
  const retry = h.submit(); assert.equal(h.calls.length, 2);
  h.deferred[1].resolve(h.response({ gameId: 12, saved: false })); await retry;
  assert.equal(h.forms[0].dataset.saved, 'false');
});

test('expired login and CSRF have recovery links without navigating', async () => {
  for (const [status, code, expected] of [[401, 'login_required', '/gamestore/login'], [403, 'csrf_invalid', 'http://localhost/gamestore/browse?page=2']]) {
    const h = harness(), original = h.location.href, pending = h.submit();
    h.deferred[0].resolve(h.response({ error: code }, status)); await pending;
    assert.equal(h.forms[0].dataset.saved, 'false'); assert.equal(h.recovery.href, expected);
    assert.equal(h.recovery.hidden, false); assert.equal(h.location.href, original);
  }
});

test('missing game, service failure, malformed state and HTML never confirm a heart', async () => {
  for (const [body, status, type] of [[{ error: 'game_not_found' }, 404], [{ error: 'save_unconfirmed' }, 503], [{ gameId: 99, saved: true }, 200], [{ gameId: 12, saved: 'true' }, 200], [{}, 200, 'text/html']]) {
    const h = harness(), pending = h.submit();
    h.deferred[0].resolve(h.response(body, status, type)); await pending;
    assert.equal(h.forms[0].dataset.saved, 'false'); assert.equal(h.forms[0].button.disabled, false);
    assert.equal(h.notice.classList.values.has('is-error'), true);
  }
});

test('wishlist replaces only its content and clamps a removed last page without navigation', async () => {
  const h = harness({ ids: ['12'], saved: true, list: true });
  const oldRoot = h.root, pending = h.submit(), replacement = h.nextPage(1, ['13', '14']);
  h.deferred[0].resolve(h.response({ gameId: 12, saved: false })); await tick();
  assert.equal(h.calls[1].options.headers.Accept, 'text/html');
  h.deferred[1].resolve(h.response(null, 200, 'text/html')); await pending;
  assert.notEqual(h.root, oldRoot); assert.equal(h.root, replacement);
  assert.equal(h.location.href, 'http://localhost/gamestore/wishlist#games');
  assert.equal(h.history.changes.length, 1);
  const next = h.submit(h.forms[0]);
  h.deferred[2].resolve(h.response({ gameId: 13, saved: false })); await tick();
  h.nextPage(1, []); h.deferred[3].resolve(h.response(null, 200, 'text/html')); await next;
  assert.equal(h.forms.length, 0);
});

test('confirmed save is retained if refreshing the list fails or belongs to another session', async () => {
  for (const changedSession of [false, true]) {
    const h = harness({ ids: ['12'], saved: true, list: true }), oldRoot = h.root, pending = h.submit();
    h.deferred[0].resolve(h.response({ gameId: 12, saved: false })); await tick();
    h.nextPage(1, [], 'different-session');
    h.deferred[1].resolve(h.response(null, changedSession ? 200 : 503, 'text/html')); await pending;
    assert.equal(h.root, oldRoot); assert.equal(h.forms[0].dataset.saved, 'false');
    assert.equal(h.recovery.href, h.location.href); assert.equal(h.forms[0].button.disabled, false);
  }
});

test('wishlist serializes mutations across games until refreshed content arrives', async () => {
  const h = harness({ ids: ['12', '13'], saved: true, list: true }), pending = h.submit();
  assert.equal(h.forms[1].button.disabled, true);
  await h.submit(h.forms[1]); assert.equal(h.calls.length, 1);
  h.deferred[0].resolve(h.response({ gameId: 12, saved: false })); await tick();
  await h.submit(h.forms[1]); assert.equal(h.calls.length, 2);
  h.nextPage(1, ['13']); h.deferred[1].resolve(h.response(null, 200, 'text/html')); await pending;
  assert.equal(h.forms[0].button.disabled, false);
});

test('success notices disappear while errors remain readable', async () => {
  const h = harness(), pending = h.submit();
  h.deferred[0].resolve(h.response({ gameId: 12, saved: true })); await pending;
  assert.equal(h.timers.size, 1);
  const timer = [...h.timers.values()][0]; assert.equal(timer.delay, 4000);
  timer.callback(); assert.equal(h.notice.hidden, true);
  const failed = h.submit(); h.deferred[1].reject(new Error('Offline')); await failed;
  assert.equal(h.notice.hidden, false); assert.equal(h.timers.size, 0);
});
