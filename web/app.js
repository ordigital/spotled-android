import * as P from './protocol.js';
import {parseSlf, parseYaff, reflow, linesToFrames} from './fonts.js';

const W = P.W, H = P.H, MAXF = P.MAX_FRAMES, $ = id => document.getElementById(id);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const store = {get: k => { try { return localStorage.getItem(k); } catch { return null; } }, set: (k, v) => { try { localStorage.setItem(k, v); } catch {} }};

// ---------- icons ----------
const IC = {
  folder: 'M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z',
  save: 'M17 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z',
  edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  move: 'M10 9h4V6h3l-5-5-5 5h3v3zm-1 1H6V7l-5 5 5 5v-3h3v-4zm14 2l-5-5v3h-3v4h3v3l5-5zm-9 3h-4v3H7l5 5 5-5h-3v-3z',
  build: 'M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z',
  text: 'M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z',
  grid: 'M3 3v18h18V3H3zm8 16H5v-6h6v6zm0-8H5V5h6v6zm8 8h-6v-6h6v6zm0-8h-6V5h6v6z',
  gallery: 'M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2zm-11-4l2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 2h14v-2H4V6H2z',
  send: 'M2.01 21L23 12 2.01 3 2 10l15 2-15 2z',
  undo: 'M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z',
  redo: 'M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z',
  play: 'M8 5v14l11-7z', stop: 'M6 6h12v12H6z',
  globe: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2 0 .68.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2 0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 0 .68-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z',
  sun: 'M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z',
  moon: 'M10 2c-1.82 0-3.53.5-5 1.35C7.99 5.08 10 8.3 10 12s-2.01 6.92-5 8.65C6.47 21.5 8.18 22 10 22c5.52 0 10-4.48 10-10S15.52 2 10 2z',
  prev: 'M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z', next: 'M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  copy: 'M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z',
  del: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
};
const svg = k => `<svg viewBox="0 0 24 24"><path d="${IC[k]}"/></svg>`;

// ---------- i18n ----------
const LANGS = [['en', 'English'], ['pl', 'Polski'], ['de', 'Deutsch'], ['fr', 'Français'], ['es', 'Español'], ['it', 'Italiano'], ['cs', 'Čeština'], ['hu', 'Magyar'], ['uk', 'Українська'], ['ru', 'Русский'], ['zh', '中文'], ['ja', '日本語']];
let dict = {}, en = {}, lang = store.get('lang') || (LANGS.find(l => (navigator.language || '').startsWith(l[0])) || LANGS[0])[0];
const t = (k, ...a) => { let i = 0; return String(dict[k] ?? en[k] ?? k).replace(/%[sd]/g, () => a[i++]); };
async function loadLang(code) {
  if (!Object.keys(en).length) en = await (await fetch('i18n/en.json')).json();
  dict = code === 'en' ? en : await (await fetch(`i18n/${code}.json`)).json();
  lang = code; store.set('lang', code); document.documentElement.lang = code;
}

// ---------- state ----------
const blank = () => new Uint8Array(W * H);
const eq = (a, b) => a.every((v, i) => v === b[i]);
let P0 = () => ({frames: [blank()], current: 0, imgEffect: 'NONE', imgSpeed: 150, text: '', txtEffect: 'SCROLL_LEFT', txtSpeed: 0, twoLines: false});
let proj = P0(), view = 'board', mode = 'draw', name = null, playing = false, playTimer = null;
let dark = store.get('dark') === '1', fontId = store.get('font') || 'builtin';
let placement = null, fonts = null, customFonts = [];
const hist = {edits: [], pos: 0};
const cur = () => proj.frames[proj.current];
const setCur = f => { proj.frames[proj.current] = f; };
function record(idx, before, after) {
  if (eq(before, after)) return;
  hist.edits.length = hist.pos; hist.edits.push({idx, before, after}); hist.pos = hist.edits.length;
}
const histReset = () => { hist.edits = []; hist.pos = 0; };
function undo() { if (busy() || hist.pos === 0) return; const e = hist.edits[--hist.pos]; proj.frames[e.idx] = e.before; proj.current = e.idx; render(); }
function redo() { if (busy() || hist.pos >= hist.edits.length) return; const e = hist.edits[hist.pos++]; proj.frames[e.idx] = e.after; proj.current = e.idx; render(); }
const busy = () => playing || placement;

// ---------- project JSON ----------
const rowsOf = f => Array.from({length: H}, (_, y) => Array.from(f.subarray(y * W, y * W + W), v => v ? '1' : '0').join(''));
const toJson = () => JSON.stringify({version: 1, tab: view === 'text' ? 1 : 0, current_frame: proj.current,
  image: {frames: proj.frames.map(rowsOf), effect: proj.imgEffect, speed: proj.imgSpeed},
  text: {content: proj.text, effect: proj.txtEffect, speed: proj.txtSpeed, two_lines: proj.twoLines}}, null, 1);
function fromJson(s) {
  const j = JSON.parse(s), img = j.image || {}, tx = j.text || {};
  const fr = (img.frames || []).map(rows => { const f = blank(); for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) f[y * W + x] = rows[y]?.[x] === '1' ? 1 : 0; return f; });
  const effect = e => P.EFFECTS.includes(e) ? e : 'NONE';
  const p = P0();
  p.frames = fr.length ? fr : [blank()];
  p.current = Math.min(Math.max(0, j.current_frame | 0), p.frames.length - 1);
  p.imgEffect = effect(img.effect); p.imgSpeed = Math.min(3500, Math.max(1, img.speed ?? 100));
  p.text = tx.content || ''; p.txtEffect = P.EFFECTS.includes(tx.effect) ? tx.effect : 'SCROLL_LEFT';
  p.txtSpeed = Math.min(3500, Math.max(0, tx.speed ?? 0)); p.twoLines = !!tx.two_lines;
  return {p, tab: j.tab === 1 ? 'text' : 'board'};
}
function loadProject(s, nm) {
  try { const r = fromJson(s); stopPlay(); placement = null; proj = r.p; view = r.tab; name = nm; histReset(); render(); }
  catch (e) { message(t('error'), t('err_load', e.message)); }
}

// ---------- UI shell ----------
function applyTheme() { document.documentElement.dataset.theme = dark ? 'dark' : 'light'; }
function btn(label, icon, fn, cls = 'chip', dis = false) {
  const b = document.createElement('button'); b.className = cls; b.disabled = dis;
  b.innerHTML = (icon ? svg(icon) : '') + (label ? `<span>${label}</span>` : ''); b.onclick = fn; return b;
}
function renderRail() {
  const r = $('rail'); r.innerHTML = '';
  const add = (label, icon, fn, cls = '') => { const b = btn(label, icon, fn, 'rb ' + cls); r.append(b); };
  add(t('open'), 'folder', () => $('fopen').click());
  add(t('save'), 'save', saveFile);
  if (view === 'board') {
    add(mode === 'draw' ? t('draw') : t('shift'), mode === 'draw' ? 'edit' : 'move', () => { mode = mode === 'draw' ? 'shift' : 'draw'; render(); });
    add(t('tools'), 'build', () => { if (!busy()) openTools(); });
  }
  add(view === 'text' ? t('board') : t('text'), view === 'text' ? 'grid' : 'text', () => { commitPlacement(); stopPlay(); view = view === 'text' ? 'board' : 'text'; render(); }, view === 'text' ? 'sel' : '');
  add(t('gallery'), 'gallery', () => { commitPlacement(); stopPlay(); openGallery(); });
  add(t('send'), 'send', openSend, 'accent');
}
function renderTop() {
  const top = $('top'); top.innerHTML = '';
  const title = document.createElement('div'); title.id = 'title'; title.textContent = name || t('new_project'); top.append(title);
  if (view === 'board') {
    top.append(btn('', 'undo', undo, 'chip', busy() || hist.pos === 0), btn('', 'redo', redo, 'chip', busy() || hist.pos >= hist.edits.length),
      btn(playing ? t('stop') : t('play'), playing ? 'stop' : 'play', () => playing ? stopPlay() : startPlay()));
  }
  top.append(btn('', 'globe', openLang), btn('', dark ? 'sun' : 'moon', () => { dark = !dark; store.set('dark', dark ? '1' : '0'); applyTheme(); render(); }));
  top.querySelectorAll('button').forEach((b, i, a) => { b.title = ''; });
}
const A = (u, txt) => `<a href="${u}" target="_blank" rel="noopener">${txt}</a>`;
function applyStatic() {
  $('t-lead').textContent = t('lead'); $('t-unofficial').textContent = t('unofficial'); $('t-suppi').textContent = t('suppi_support');
  $('t-more').innerHTML = '<strong>' + t('lead_app', A('https://github.com/ordigital/spotled-android/releases/latest', t('link_release'))) + '</strong> ' + t('lead_info', A('https://github.com/ordigital/spotled-android', t('link_project')));
  $('t-ghbtn').textContent = t('github_btn');
  $('t-rot').textContent = t('rotate_msg'); $('fsbtn').textContent = t('fullscreen_btn');
  $('t-readme').textContent = t('readme_link'); $('t-linux').textContent = t('linux_note'); document.title = 'SpotLED Editor';
}
function render() { applyTheme(); applyStatic(); renderRail(); renderTop(); renderView(); }

// ---------- board view ----------
const CELL = 10; let cv, g;
const composed = () => placement ? blit(placement.base, placement.stamp, placement.ox, placement.oy) : cur();
function blit(base, st, ox, oy) {
  const f = base.slice();
  for (let y = 0; y < st.h; y++) for (let x = 0; x < st.w; x++) { const fx = ox + x, fy = oy + y; if (fx >= 0 && fx < W && fy >= 0 && fy < H) f[fy * W + fx] = st.bits[y * st.w + x]; }
  return f;
}
function drawFrame(ctx, f, prev) {
  ctx.fillStyle = '#050805'; ctx.fillRect(0, 0, W * CELL, H * CELL);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    ctx.fillStyle = f[y * W + x] ? '#4eff00' : (prev && prev[y * W + x]) ? '#14301a' : '#0f1e0f';
    ctx.fillRect(x * CELL + 1, y * CELL + 1, CELL - 2, CELL - 2);
  }
}
const drawBoard = () => cv && drawFrame(g, composed(), !playing && proj.current > 0 ? proj.frames[proj.current - 1] : null);

function cellAt(e) { const r = cv.getBoundingClientRect(); return [Math.floor((e.clientX - r.left) / r.width * W), Math.floor((e.clientY - r.top) / r.height * H)]; }
let stroke = null;
function paintLine(f, x0, y0, x1, y1, v) {
  const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1; let err = dx + dy, x = x0, y = y0;
  for (;;) { if (x >= 0 && x < W && y >= 0 && y < H) f[y * W + x] = v; if (x === x1 && y === y1) break; const e2 = 2 * err; if (e2 >= dy) { err += dy; x += sx; } if (e2 <= dx) { err += dx; y += sy; } }
}
function shifted(f, dx, dy) { const o = blank(); for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const sx = x - dx, sy = y - dy; if (sx >= 0 && sx < W && sy >= 0 && sy < H) o[y * W + x] = f[sy * W + sx]; } return o; }
function bindBoard() {
  cv.onpointerdown = e => {
    if (playing) return; cv.setPointerCapture(e.pointerId); const [x, y] = cellAt(e);
    if (placement) stroke = {kind: 'place', gx: x - placement.ox, gy: y - placement.oy};
    else if (mode === 'draw') { const before = cur(), v = x >= 0 && x < W && y >= 0 && y < H && before[y * W + x] ? 0 : 1; stroke = {kind: 'draw', before, v, lx: x, ly: y}; const f = before.slice(); paintLine(f, x, y, x, y, v); setCur(f); }
    else stroke = {kind: 'shift', before: cur(), sx: x, sy: y};
    drawBoard();
  };
  cv.onpointermove = e => {
    if (!stroke) return; const [x, y] = cellAt(e);
    if (stroke.kind === 'place') { placement.ox = Math.max(-placement.stamp.w + 1, Math.min(W - 1, x - stroke.gx)); placement.oy = Math.max(-placement.stamp.h + 1, Math.min(H - 1, y - stroke.gy)); }
    else if (stroke.kind === 'draw') { const f = cur().slice(); paintLine(f, stroke.lx, stroke.ly, x, y, stroke.v); stroke.lx = x; stroke.ly = y; setCur(f); }
    else setCur(shifted(stroke.before, x - stroke.sx, y - stroke.sy));
    drawBoard();
  };
  cv.onpointerup = cv.onpointercancel = () => {
    if (stroke && stroke.kind !== 'place') { record(proj.current, stroke.before, cur()); renderTop(); }
    stroke = null;
  };
}
function startPlay() {
  commitPlacement(); playing = true; let i = 0;
  const tick = () => { proj.current = i; i = (i + 1) % proj.frames.length; drawBoard(); updateNav(); playTimer = setTimeout(tick, Math.max(10, proj.imgSpeed)); };
  render(); tick();
}
function stopPlay() { if (!playing) return; playing = false; clearTimeout(playTimer); render(); }
function edit(fn) { if (busy()) return; const b = cur(); setCur(fn(b)); record(proj.current, b, cur()); render(); }
function addFrame() { if (busy()) return; if (proj.frames.length >= MAXF) return message(t('error'), t('max_frames', MAXF)); proj.current++; proj.frames.splice(proj.current, 0, blank()); histReset(); render(); }
function delFrame() { if (busy()) return; if (proj.frames.length === 1) proj.frames = [blank()]; else { proj.frames.splice(proj.current, 1); proj.current = Math.min(proj.current, proj.frames.length - 1); } histReset(); render(); }
function goTo(i) { if (!busy()) { proj.current = Math.max(0, Math.min(proj.frames.length - 1, i)); render(); } }
function commitPlacement() { if (!placement) return; const b = placement.base; setCur(composed()); record(proj.current, b, cur()); placement = null; render(); }
function updateNav() { const n = document.getElementById('fnum'); if (n) n.textContent = `${proj.current + 1} / ${proj.frames.length}`; }
const effectSel = (val, fn) => { const s = document.createElement('select'); P.EFFECTS.forEach(e => s.add(new Option(e, e, false, e === val))); s.onchange = () => fn(s.value); return s; };
function speedRow(label, min, get, set, unit) {
  const d = document.createElement('div'); d.className = 'row';
  const r = document.createElement('input'); r.type = 'range'; r.min = min; r.max = 3500; r.value = get();
  const v = document.createElement('b'); v.style.minWidth = '64px'; const show = () => v.textContent = get() + unit;
  r.oninput = () => { set(+r.value); show(); }; show();
  d.append(label, r, v); return d;
}
const lab = s => { const e = document.createElement('span'); e.textContent = s; e.style.color = 'var(--dim)'; return e; };
function renderView() {
  const v = $('view'); v.innerHTML = '';
  if (view === 'board') {
    cv = document.createElement('canvas'); cv.id = 'cv'; cv.width = W * CELL; cv.height = H * CELL; g = cv.getContext('2d'); v.append(cv); bindBoard(); drawBoard();
    const nav = document.createElement('div'); nav.className = 'row';
    const num = document.createElement('b'); num.id = 'fnum';
    const dots = document.createElement('div'); dots.id = 'dots';
    proj.frames.forEach((_, i) => { const d = document.createElement('i'); if (i === proj.current) d.className = 'on'; d.onclick = () => goTo(i); dots.append(d); });
    const dis = busy();
    nav.append(btn('', 'prev', () => goTo(proj.current - 1), 'chip', dis), num, btn('', 'next', () => goTo(proj.current + 1), 'chip', dis), dots,
      btn('', 'plus', addFrame, 'chip', dis), btn('', 'copy', () => { if (proj.current > 0) edit(() => proj.frames[proj.current - 1].slice()); }, 'chip', dis || proj.current === 0), btn('', 'del', delFrame, 'chip', dis));
    v.append(nav); updateNav();
    if (placement) { const pr = document.createElement('div'); pr.className = 'row'; pr.append(lab(t('drag_to_place')), btn(t('confirm'), null, commitPlacement, 'chip pri'), btn(t('cancel'), null, () => { placement = null; render(); })); v.append(pr); }
    const er = document.createElement('div'); er.className = 'row'; er.append(lab(t('effect')), effectSel(proj.imgEffect, x => proj.imgEffect = x));
    v.append(er); v.append(speedRow(lab(t('speed')), 1, () => proj.imgSpeed, x => proj.imgSpeed = x, ' ms'));
  } else {
    const ta = document.createElement('input'); ta.type = 'text'; ta.id = 'txt'; ta.value = proj.text; ta.placeholder = t('type_text'); ta.oninput = () => proj.text = ta.value; v.append(ta);
    const fr = document.createElement('div'); fr.className = 'row'; const fs = document.createElement('select');
    fs.add(new Option(t('font_builtin'), 'builtin', false, fontId === 'builtin'));
    allSlf().forEach(f => fs.add(new Option(f.name, f.id, false, fontId === f.id)));
    fs.onchange = () => { fontId = fs.value; store.set('font', fontId); if (fontId !== 'builtin') proj.twoLines = false; render(); };
    const builtin = fontId === 'builtin' || !allSlf().some(f => f.id === fontId);
    const cb = document.createElement('input'); cb.type = 'checkbox'; cb.checked = proj.twoLines && builtin; cb.disabled = !builtin; cb.onchange = () => proj.twoLines = cb.checked;
    const cl = document.createElement('label'); cl.append(cb, ' ' + t('two_lines'));
    fr.append(lab(t('font')), fs, btn('+ .slf', null, () => $('fslf').click()), cl); v.append(fr);
    const er = document.createElement('div'); er.className = 'row'; er.append(lab(t('effect')), effectSel(proj.txtEffect, x => proj.txtEffect = x)); v.append(er);
    v.append(speedRow(lab(t('speed')), 0, () => proj.txtSpeed, x => proj.txtSpeed = x, ''));
  }
}
const allSlf = () => fonts ? [...fonts.slf, ...customFonts] : [];

// ---------- modal ----------
let onClose = null;
function modal(title, body, actions = []) {
  const m = $('modal'); m.innerHTML = ''; const h = document.createElement('h3'); h.textContent = title; m.append(h);
  if (typeof body === 'string') { const p = document.createElement('p'); p.textContent = body; m.append(p); } else if (body) m.append(body);
  const row = document.createElement('div'); row.className = 'row'; row.style.justifyContent = 'flex-end';
  actions.forEach(a => row.append(a)); m.append(row); $('modalbg').style.display = 'flex';
}
function closeModal() { $('modalbg').style.display = 'none'; if (onClose) { onClose(); onClose = null; } }
$('modalbg').onclick = e => { if (e.target.id === 'modalbg') closeModal(); };
const message = (title, body) => modal(title, body, [btn(t('ok'), null, closeModal, 'chip pri')]);
const closeBtn = () => btn(t('close'), null, closeModal);

function openLang() {
  const gr = document.createElement('div'); gr.className = 'grid';
  LANGS.forEach(([c, n]) => { const b = btn(n, null, async () => { await loadLang(c); closeModal(); render(); }, 'chip' + (c === lang ? ' sel' : '')); b.style.justifyContent = 'center'; gr.append(b); });
  modal(t('language'), gr, [closeBtn()]);
}
function openTools() {
  const gr = document.createElement('div'); gr.className = 'grid';
  const items = [[t('tool_clear'), () => edit(() => blank())], [t('tool_invert'), () => edit(f => f.map(v => v ? 0 : 1))],
    [t('tool_mirror_h'), () => edit(f => f.map((_, i) => f[Math.floor(i / W) * W + (W - 1 - i % W)]))], [t('tool_mirror_v'), () => edit(f => f.map((_, i) => f[(H - 1 - Math.floor(i / W)) * W + i % W]))],
    [t('tool_import_png'), () => $('fpng').click()], [t('tool_insert_text'), openInsertText]];
  items.forEach(([n, fn]) => gr.append(btn(n, null, () => { closeModal(); fn(); })));
  modal(t('tools'), gr, [closeBtn()]);
}
function openInsertText() {
  const inp = document.createElement('input'); inp.type = 'text'; inp.style.width = '100%'; inp.placeholder = t('text_label');
  const sel = document.createElement('select'); allSlf().forEach(f => sel.add(new Option(f.name, f.id)));
  const d = document.createElement('div'); d.className = 'row'; d.style.flexDirection = 'column'; d.style.alignItems = 'stretch'; d.append(inp, sel);
  modal(t('insert_text_title'), d, [btn(t('cancel'), null, closeModal), btn(t('ok'), null, () => {
    const st = allSlf().find(f => f.id === sel.value).textStamp(inp.value);
    if (!st) return; closeModal(); placement = {stamp: st, base: cur(), ox: Math.floor((W - st.w) / 2), oy: Math.floor((H - st.h) / 2)}; render();
  }, 'chip pri')]);
  inp.focus();
}
async function openGallery() {
  const gr = document.createElement('div'); gr.className = 'grid'; const timers = [];
  modal(t('gallery'), gr, [closeBtn()]);
  onClose = () => timers.forEach(clearInterval);
  try {
    for (const f of await (await fetch('gallery/index.json')).json()) {
      const txt = await (await fetch('gallery/' + encodeURIComponent(f))).text(), p = fromJson(txt).p;
      const it = document.createElement('div'); it.className = 'gi'; const c = document.createElement('canvas'); c.width = W * CELL; c.height = H * CELL;
      const ctx = c.getContext('2d'), cap = document.createElement('div'); cap.textContent = f.replace(/\.json$/, ''); it.append(c, cap); gr.append(it);
      let i = 0; const draw = () => { drawFrame(ctx, p.frames[i], null); i = (i + 1) % p.frames.length; }; draw(); timers.push(setInterval(draw, Math.max(40, p.imgSpeed)));
      it.onclick = () => { closeModal(); loadProject(txt, f); proj.current = 0; render(); };
    }
  } catch (e) { gr.textContent = String(e); }
}

// ---------- files ----------
function saveFile() {
  commitPlacement(); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([toJson()], {type: 'application/json'}));
  a.download = name || "spotled.json"; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
$('fopen').onchange = async e => { const f = e.target.files[0]; e.target.value = ''; if (f) loadProject(await f.text(), f.name); };
$('fslf').onchange = async e => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  try { customFonts.push(parseSlf(f.name, await f.text(), f.name.replace(/\.[^.]+$/, ''))); fontId = f.name; render(); } catch (err) { message(t('error'), err.message); }
};
$('fpng').onchange = async e => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  try {
    const bmp = await createImageBitmap(f), c = document.createElement('canvas'); c.width = bmp.width; c.height = bmp.height;
    const x = c.getContext('2d'); x.drawImage(bmp, 0, 0); const d = x.getImageData(0, 0, c.width, c.height).data, bits = new Uint8Array(c.width * c.height);
    for (let i = 0; i < bits.length; i++) { const r = d[i * 4], gg = d[i * 4 + 1], b = d[i * 4 + 2], a = d[i * 4 + 3]; bits[i] = a >= 128 && (Math.max(r, gg, b) + Math.min(r, gg, b)) / 2 >= 128 ? 1 : 0; }
    if (!busy()) { placement = {stamp: {w: c.width, h: c.height, bits}, base: cur(), ox: 0, oy: 0}; placement.ox = Math.floor((W - c.width) / 2); placement.oy = Math.floor((H - c.height) / 2); render(); }
  } catch (err) { message(t('error'), t('err_import', t('err_invalid_image'))); }
};

// ---------- BLE send ----------
class BleErr extends Error {}
let lastDev = null;
class Link {
  constructor(log) { this.log = log; this.q = []; this.waiter = null; }
  async open() {
    if (!navigator.bluetooth) throw new BleErr('Web Bluetooth is not available. Use Chrome, Chromium or Edge (https or localhost).');
    this.dev = lastDev || await navigator.bluetooth.requestDevice({filters: [{namePrefix: 'SPOTLED_'}, {namePrefix: 'SpotLED_'}], optionalServices: [P.SERVICE]});
    lastDev = this.dev; this.log('device: ' + this.dev.name);
    let svc;
    for (let i = 0; i < 6 && !svc; i++) {
      await sleep(i ? 1000 : 500);
      try { if (!this.dev.gatt.connected) await this.dev.gatt.connect(); svc = await this.dev.gatt.getPrimaryService(P.SERVICE); }
      catch (e) { this.log(`retry ${i + 1}/6: ${e.message}`); }
    }
    if (!svc) throw new BleErr(t('ble_not_spotled') + ' (Linux/BlueZ? see README)');
    this.cmd = await svc.getCharacteristic(P.CMD); this.data = await svc.getCharacteristic(P.DATA);
    this.cmd.addEventListener('characteristicvaluechanged', ev => { const dv = ev.target.value; this.q.push(new Uint8Array(dv.buffer.slice(dv.byteOffset, dv.byteOffset + dv.byteLength))); if (this.waiter) { this.waiter(); this.waiter = null; } });
    this.log('connected, enabling notifications');
    await this.cmd.startNotifications(); this.log('notifications enabled');
  }
  async write(ch, bytes) {
    for (let i = 0; i < 50; i++) { try { await (ch.writeValueWithoutResponse ? ch.writeValueWithoutResponse(bytes) : ch.writeValue(bytes)); return; } catch (e) { if (i === 49) { this.log('write error: ' + (e.name || '') + ' ' + (e.message || e)); throw new BleErr(t('ble_write_fail') + ' (' + (e.message || e) + ')'); } await sleep(20); } }
  }
  async next(timeout = 1500) {
    if (!this.q.length) await Promise.race([new Promise(r => this.waiter = r), sleep(timeout)]);
    if (!this.q.length) throw new BleErr(t('ble_timeout_resp'));
    const v = this.q.shift(); try { return P.parseResponse(v); } catch { throw new BleErr(t('ble_bad_resp', [...v].map(x => x.toString(16).padStart(2, '0')).join(' '))); }
  }
  async query(cmd) { for (let i = 0; i < 4; i++) { this.q.length = 0; await this.write(this.cmd, cmd); try { return await this.next(); } catch (e) { if (i === 3) throw e; } } }
  close() { try { this.dev.gatt.disconnect(); } catch {} }
}
const rowStrs = f => f.map(r => Array.from(r, v => v ? '1' : '0').join(''));
/** Returns a function (width,height,frameLimit) -> [content blobs], plus a result message. */
function buildJob() {
  if (view === 'board') {
    return {msg: t('sent_frames', proj.frames.length), contents: (w, h, lim) => {
      if (proj.frames.length > Math.min(MAXF, lim)) throw new BleErr(t('max_frames', Math.min(MAXF, lim)));
      return [P.animation(proj.frames.map(rowsOf), proj.imgSpeed, 0, P.EFFECTS.indexOf(proj.imgEffect))];
    }};
  }
  const text = proj.text; if (!text.trim()) throw new BleErr(t('enter_text'));
  const slf = allSlf().find(f => f.id === fontId), eff = P.EFFECTS.indexOf(proj.txtEffect);
  if (!slf) return {msg: t('sent_text'), contents: (w, h, lim) => {
    const two = proj.twoLines, f = two ? fonts.yaff4x6 : fonts.yaff6x12;
    const fr = two ? linesToFrames(reflow(text, f, w), f, true, w, Math.max(1, h / 6 | 0), 6) : linesToFrames(text.replace(/\r/g, '').split('\n'), f, false, w, Math.max(1, h / 12 | 0), 12);
    if (fr.length > lim) throw new BleErr('The animation exceeds the device frame limit.');
    return [P.animation(fr.map(rowStrs), 2000, proj.txtSpeed & 0xFF, eff)];
  }};
  if (text.includes('\n')) throw new BleErr(t('custom_one_line'));
  return {msg: t('sent_text'), contents: () => {
    const chars = [...new Set([...text])].map(c => ({code: c.charCodeAt(0), width: slf.width, height: slf.height, rows: slf.glyph(c)}));
    return [P.fontData(chars), P.textData(text, Math.round(proj.txtSpeed / 3500 * 255), eff)];
  }};
}
function openSend() {
  commitPlacement(); stopPlay();
  const box = document.createElement('div'), status = document.createElement('p'), prog = document.createElement('div'), bar = document.createElement('i'), log = document.createElement('pre');
  prog.className = 'prog'; prog.append(bar); status.textContent = t('choose_board'); log.classList.add('hide'); box.append(status, prog, log);
  const lg = s => { log.classList.remove('hide'); log.textContent += s + '\n'; log.scrollTop = 1e9; };
  const go = btn(view === 'board' ? t('send_frames', proj.frames.length) : t('send_text'), 'send', async () => {
    go.disabled = true; bar.style.width = '0'; log.textContent = '';
    let job; const link = new Link(lg);
    try {
      job = buildJob(); status.textContent = t('searching'); await link.open();
      let bufferSize = 240, w = W, h = H, lim = MAXF;
      const b = await link.query(P.GET_BUFFER_SIZE); if (b.kind === 'buffer' && b.size >= P.CHUNK) bufferSize = b.size;
      const d = await link.query(P.GET_DISPLAY_INFO); if (d.kind === 'display' && d.width > 0 && d.height > 0 && d.frameLimit > 0) { w = d.width; h = d.height; lim = d.frameLimit; }
      const contents = job.contents(w, h, lim), total = contents.reduce((a, c) => a + c.length + 15, 0); let done = 0, dataSerial = 0, cmdSerial = 0;
      for (const content of contents) {
        dataSerial++; cmdSerial = (cmdSerial + 1) & 0xFFFF; const payload = P.sendData(content, dataSerial), ty = P.SEND_DATA_TYPE;
        link.q.length = 0; await link.write(link.cmd, P.startCmd(cmdSerial, ty, payload.length));
        const r = await link.next(); if (r.kind !== 'sending' || r.serial !== cmdSerial || r.error !== 0) throw new BleErr(t('ble_unexpected_start', JSON.stringify(r)));
        const perBatch = Math.max(1, Math.floor(bufferSize / P.CHUNK)); let seek = 0, n = 0;
        while (seek < payload.length) {
          await link.write(link.data, payload.slice(seek, Math.min(seek + P.CHUNK, payload.length))); n++; seek += P.CHUNK;
          if (n >= perBatch) { n = 0; const c = await link.next(); if (c.kind !== 'continue' || c.serial !== cmdSerial) throw new BleErr(t('ble_unexpected_flow', JSON.stringify(c))); seek = c.from; }
          bar.style.width = (100 * (done + Math.min(seek, payload.length)) / total) + '%';
        }
        await link.write(link.cmd, P.finishCmd(cmdSerial, ty, payload.length)); try { await link.next(); } catch {}
        done += content.length + 15;
      }
      bar.style.width = '100%'; status.textContent = job.msg;
    } catch (e) { lg('ERROR: ' + (e.message || e)); status.textContent = e instanceof BleErr ? e.message : t('generic_error', e.message || e); lastDev = null; }
    finally { link.close(); go.disabled = false; }
  }, 'chip pri');
  modal(t('send_title'), box, [closeBtn(), go]);
}

// ---------- full screen (phones in portrait) ----------
$('fsbtn').onclick = async () => {
  try { await $('app').requestFullscreen(); } catch {}
  try { await screen.orientation.lock('landscape'); } catch {}
};
document.addEventListener('fullscreenchange', () => { if (!document.fullscreenElement) { try { screen.orientation.unlock(); } catch {} } });
// ---------- keys & init ----------
addEventListener('keydown', e => {
  if (!e.ctrlKey || e.target.tagName === 'INPUT') return;
  if (e.key === 'z') { e.preventDefault(); undo(); } else if (e.key === 'y') { e.preventDefault(); redo(); }
});
(async () => {
  await loadLang(lang);
  const text = n => fetch('fonts/' + n).then(r => r.text());
  const [a, b, c] = await Promise.all([text('amstrad_cpc.slf'), text('6x12.yaff'), text('4x6.yaff')]);
  fonts = {slf: [parseSlf('amstrad_cpc', a, 'amstrad_cpc')], yaff6x12: parseYaff('6x12', b), yaff4x6: parseYaff('4x6', c)};
  try { const f = 'i love retro.json', r = fromJson(await (await fetch('gallery/' + encodeURIComponent(f))).text()); proj = r.p; proj.current = 0; view = 'board'; name = f; } catch {}
  render();
})();
