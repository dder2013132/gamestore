const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/js/hero.js', 'utf8');

function harness(size = 5, reducedMotion = false) {
  const timers = new Map(); let sequence = 0, observer;
  const node = () => ({ hidden: true, attrs: {}, events: {}, classList: { add() {} },
    setAttribute(k, v) { this.attrs[k] = v; }, addEventListener(k, fn) { this.events[k] = fn; },
    fire(k, event = {}) { this.events[k]?.(event); }, focus() { this.focused = true; } });
  const slides = Array.from({ length: size }, (_, i) => ({ hidden: i !== 0 }));
  const dots = Array.from({ length: size }, node);
  const root = node(), controls = node(), track = node(), play = node(), icon = node(), count = node(), prev = node(), next = node();
  const elements = { '[data-hero-controls]': controls, '[data-hero-slides]': track, '[data-hero-play]': play,
    '[data-hero-play-icon]': icon, '[data-hero-count]': count, '[data-hero-prev]': prev, '[data-hero-next]': next };
  root.querySelector = key => elements[key];
  root.querySelectorAll = key => key === '[data-hero-slide]' ? slides : dots;
  const document = { ...node(), hidden: false, querySelectorAll: () => [root] };
  const motion = { ...node(), matches: reducedMotion };
  class IntersectionObserver { constructor(callback) { observer = callback; } observe() {} }
  const window = { ...node(), matchMedia: () => motion, IntersectionObserver };
  vm.runInNewContext(source, { document, window, IntersectionObserver,
    setTimeout(callback, delay) { timers.set(++sequence, { callback, delay }); return sequence; },
    clearTimeout(id) { timers.delete(id); } });
  document.fire('DOMContentLoaded');
  return { slides, dots, root, controls, track, play, icon, count, prev, next, document, motion, window, timers,
    current: () => slides.findIndex(slide => !slide.hidden),
    advance() { assert.equal(timers.size, 1); const [id, timer] = [...timers][0]; timers.delete(id); timer.callback(); },
    visible(value) { observer([{ isIntersecting: value }]); } };
}

test('rotates every six seconds with exactly one visible slide and one timer', () => {
  const h = harness(3);
  assert.equal(h.controls.hidden, false); assert.equal(h.current(), 0);
  assert.equal([...h.timers.values()][0].delay, 6000);
  assert.equal(h.track.attrs['aria-live'], 'off');
  for (const index of [1, 2, 0]) {
    h.advance(); assert.equal(h.current(), index);
    assert.equal(h.slides.filter(s => !s.hidden).length, 1);
    assert.equal(h.dots.filter(d => d.attrs['aria-current'] === 'true').length, 1);
    assert.equal(h.timers.size, 1);
  }
});

test('next, previous and direct selection wrap correctly and pause rotation', () => {
  const h = harness(5);
  h.prev.fire('click'); assert.equal(h.current(), 4); assert.equal(h.count.textContent, '05 / 05');
  assert.equal(h.timers.size, 0); assert.equal(h.play.attrs['aria-label'], '自動切替を再生');
  h.next.fire('click'); assert.equal(h.current(), 0);
  h.dots[2].fire('click'); assert.equal(h.current(), 2);
  assert.equal(h.track.attrs['aria-live'], 'polite');
  h.play.fire('click'); assert.equal(h.timers.size, 1); h.advance(); assert.equal(h.current(), 3);
});

test('hover pauses temporarily but keyboard focus stops until explicitly resumed', () => {
  const h = harness();
  h.root.fire('pointerenter', { pointerType: 'mouse' }); assert.equal(h.timers.size, 0);
  h.root.fire('pointerleave'); assert.equal(h.timers.size, 1);
  h.root.fire('focusin', { target: h.dots[0] }); assert.equal(h.timers.size, 0);
  h.root.fire('pointerleave'); assert.equal(h.timers.size, 0);
  h.root.fire('focusin', { target: h.play }); h.play.fire('click'); assert.equal(h.timers.size, 1);
});

test('off-screen and background pages do not keep rotating; restoration has one timer', () => {
  const h = harness();
  h.visible(false); assert.equal(h.timers.size, 0);
  h.visible(true); assert.equal(h.timers.size, 1);
  h.document.hidden = true; h.document.fire('visibilitychange'); assert.equal(h.timers.size, 0);
  h.document.hidden = false; h.document.fire('visibilitychange'); assert.equal(h.timers.size, 1);
  h.window.fire('pagehide'); assert.equal(h.timers.size, 0);
  h.window.fire('pageshow'); h.window.fire('pageshow'); assert.equal(h.timers.size, 1);
});

test('entry always starts playback, including reduced-motion and a restored page', () => {
  const h = harness(3, true);
  assert.equal(h.timers.size, 1); assert.equal(h.play.attrs['aria-label'], '自動切替を停止');
  h.advance(); assert.equal(h.current(), 1);
  h.play.fire('click'); assert.equal(h.timers.size, 0);
  h.window.fire('pagehide'); h.window.fire('pageshow');
  assert.equal(h.timers.size, 1); assert.equal(h.play.attrs['aria-label'], '自動切替を停止');
  h.advance(); assert.equal(h.current(), 2);
});

test('touch input does not leave the carousel stuck in a mouse hover pause', () => {
  const h = harness();
  h.root.fire('pointerenter', { pointerType: 'touch' }); assert.equal(h.timers.size, 1);
  h.play.fire('click'); assert.equal(h.timers.size, 0);
  h.play.fire('click'); assert.equal(h.timers.size, 1);
});

test('zero or one game stays static with no unnecessary controls or timer', () => {
  for (const size of [0, 1]) {
    const h = harness(size); assert.equal(h.controls.hidden, true); assert.equal(h.timers.size, 0);
    if (size) assert.equal(h.current(), 0);
  }
});

test('keyboard selection updates the slide and selected dot focus without navigating', () => {
  const h = harness(3); let prevented = 0;
  for (const [key, target] of [['End', 2], ['Home', 0], ['ArrowLeft', 2], ['ArrowRight', 0]]) {
    h.controls.fire('keydown', { key, target: h.dots[h.current()], preventDefault() { prevented++; } });
    assert.equal(h.current(), target); assert.equal(h.dots[target].focused, true);
  }
  assert.equal(prevented, 4); assert.equal(h.timers.size, 0);
  h.controls.fire('keydown', { key: 'ArrowRight', ctrlKey: true, preventDefault() { throw new Error('modified shortcut'); } });
  assert.equal(h.current(), 0);
});
