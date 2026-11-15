// Port of Fonts.kt. A glyph is an array of rows (Uint8Array of 0/1).
export class SpotFont {
  constructor(id, name, width, height, glyphs, fallbacks) { Object.assign(this, {id, name, width, height, glyphs, fallbacks}); }
  glyph(c) {
    let g = this.glyphs.get(c);
    if (!g) for (const f of this.fallbacks) { g = this.glyphs.get(f); if (g) break; }
    return g || this.glyphs.values().next().value;
  }
  glyphWidth(c) { const g = this.glyph(c); return g.length ? g[0].length : 0; }
  /** Bitmap {w,h,bits} with trimmed glyphs and 1 px spacing. */
  textStamp(text) {
    const parts = [];
    for (const ch of text) {
      const g = trimColumns(this.glyph(ch), /\s/.test(ch) ? 2 : 1);
      if (g.length && g[0].length) parts.push(g);
    }
    if (!parts.length) return null;
    const total = parts.reduce((a, g) => a + g[0].length, 0) + parts.length - 1;
    const bits = new Uint8Array(total * this.height); let x0 = 0;
    for (const g of parts) { for (let y = 0; y < g.length; y++) for (let x = 0; x < g[y].length; x++) bits[y * total + x0 + x] = g[y][x]; x0 += g[0].length + 1; }
    return {w: total, h: this.height, bits};
  }
}
export function parseSlf(id, text, stem) {
  const o = JSON.parse(text);
  const width = o.width | 0, height = o.height | 0;
  if (width <= 0 || height <= 0) throw new Error('Font width/height must be positive.');
  const chars = o.chars; if (!chars || !Object.keys(chars).length) throw new Error('Font file is missing glyph data.');
  const map = new Map();
  for (const [k, rows] of Object.entries(chars)) {
    if (!k) continue;
    map.set(k[0], Array.from({length: height}, (_, i) => { const r = rows[i] || ''; return Uint8Array.from({length: width}, (_, x) => +(r[x] === '1' || r[x] === '#')); }));
  }
  if (!map.has(' ')) map.set(' ', Array.from({length: height}, () => new Uint8Array(width)));
  return new SpotFont(id, (o.name || '').trim() || stem, width, height, map, ['?', ' ']);
}
export function parseYaff(id, text) {
  const glyphs = new Map(); let cur = null, acc = [], w = 0;
  const flush = () => { if (cur !== null && acc.length) glyphs.set(cur, acc); acc = []; cur = null; };
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (line.startsWith('#')) continue;
    if (line.endsWith(':') && (line.startsWith('0x') || line.startsWith('u+'))) {
      flush(); const n = parseInt(line.slice(2, -1), 16); cur = (Number.isFinite(n) && n <= 0xFFFF) ? String.fromCharCode(n) : null;
    } else if ((line.includes('.') || line.includes('@')) && !line.includes(':')) {
      const row = Uint8Array.from([...line].map(c => +(c === '@'))); w = Math.max(w, row.length); acc.push(row);
    }
  }
  flush();
  return new SpotFont(id, id, w, Math.max(...[...glyphs.values()].map(g => g.length)), glyphs, ['�', '\u0000', ' ']);
}
export function trimColumns(g, minWidth) {
  const h = g.length, width = g.length ? g[0].length : 0;
  const blank = w => Array.from({length: h}, () => new Uint8Array(Math.max(1, w)));
  if (!width) return blank(minWidth);
  let left = -1; for (let x = 0; x < width && left < 0; x++) if (g.some(r => r[x])) left = x;
  if (left < 0) return blank(minWidth);
  let right = width - 1; while (!g.some(r => r[right])) right--;
  return g.map(r => { const s = r.slice(left, right + 1); if (s.length >= minWidth) return s; const o = new Uint8Array(minWidth); o.set(s); return o; });
}

/** Greedy word wrap (same rules as the device library). */
export function reflow(text, font, width) {
  const tw = s => [...s].reduce((a, c) => a + font.glyphWidth(c), 0);
  const out = [];
  for (const line of text.replace(/\r/g, '').split('\n')) {
    let cur = '', remaining = width;
    line.split(' ').forEach((orig, i) => {
      const word = i ? ' ' + orig : orig, w = tw(word);
      if (remaining - w >= 0) { remaining -= w; cur += word; }
      else if (w > width) for (const c of word) {
        const cw = font.glyphWidth(c);
        if (remaining - cw >= 0) { remaining -= cw; cur += c; } else { out.push(cur); remaining = width - cw; cur = c; }
      } else { out.push(cur); remaining = width - tw(orig); cur = orig; }
    });
    out.push(cur);
  }
  return out;
}

/** Rasterise lines into frames of linesPerFrame lines. A frame = array of rows (Uint8Array). */
export function linesToFrames(lines, font, center, width, linesPerFrame, lineHeight) {
  const raster = [];
  for (const line of lines) {
    const rl = Array.from({length: lineHeight}, () => []);
    for (const c of line) {
      const g = font.glyph(c);
      if (g.length > lineHeight) throw new Error('Character height exceeds line height.');
      const gw = g.length ? g[0].length : 0, top = Math.floor((lineHeight - g.length) / 2) + ((lineHeight - g.length) % 2);
      for (let i = 0; i < lineHeight; i++) { if (i >= top && i - top < g.length) rl[i].push(...g[i - top]); else for (let k = 0; k < gw; k++) rl[i].push(0); }
    }
    while (rl[0].length > width) raster.push(rl.map(r => Uint8Array.from(r.splice(0, width))));
    const rem = width - rl[0].length;
    if (rem > 0) {
      const [l, r] = center ? [Math.floor(rem / 2), Math.floor(rem / 2) + rem % 2] : [0, rem];
      for (const row of rl) { row.unshift(...Array(l).fill(0)); row.push(...Array(r).fill(0)); }
    }
    raster.push(rl.map(r => Uint8Array.from(r)));
  }
  const frames = []; let cur = [], n = 0;
  for (const rl of raster) { if (n < linesPerFrame) { cur.push(...rl); n++; } else { frames.push(cur); cur = [...rl]; n = 1; } }
  if (cur.length) { for (let k = 0; k < linesPerFrame - n; k++) for (let j = 0; j < lineHeight; j++) cur.push(new Uint8Array(width)); frames.push(cur); }
  return frames;
}
