// SpotLED wire protocol (port of Protocol.kt). All integers big-endian.
export const SERVICE = '0000ff20-0000-1000-8000-00805f9b34fb';
export const CMD = '0000ff21-0000-1000-8000-00805f9b34fb';
export const DATA = '0000ff22-0000-1000-8000-00805f9b34fb';
export const SEND_DATA_TYPE = 0x8004, CHUNK = 20, W = 48, H = 12, MAX_FRAMES = 20;
export const EFFECTS = ['NONE', 'SCROLL_UP', 'SCROLL_DOWN', 'SCROLL_LEFT', 'SCROLL_RIGHT', 'STACK', 'EXPAND', 'LASER'];
export const GET_DISPLAY_INFO = Uint8Array.of(4, 18, 0, 0);
export const GET_BUFFER_SIZE = Uint8Array.of(4, 20, 0, 0);

class Writer {
  constructor() { this.b = []; this.start = 0; }
  begin() { this.start = this.b.length; return this; }
  u8(v) { this.b.push(v & 0xFF); return this; }
  u16(v) { return this.u8(v >> 8).u8(v); }
  u32(v) { return this.u16(Math.floor(v / 65536)).u16(v & 0xFFFF); }
  bytes(a) { for (const x of a) this.b.push(x); return this; }
  checksum() {
    let s = 0; for (let i = this.start; i < this.b.length; i++) s += this.b[i];
    return this.u8(s > 255 ? (-s) & 0xFF : s);
  }
  out() { return Uint8Array.from(this.b); }
}
const record = (w, len, type, body) => { w.begin().u32(len).u16(type); body(w); w.checksum(); };
const time = (w, ms) => record(w, 10, 7, x => x.u8(0).u16(ms));
const speed = (w, s) => record(w, 8, 9, x => x.u8(s));
const effect = (w, e) => record(w, 8, 8, x => x.u8(e));

/** rows: array of 12 strings of 48 chars '0'/'1' -> packed bytes (MSB first). */
export function packRows(rows) {
  const out = [];
  for (const r of rows) for (let i = 0; i < r.length; i += 8) {
    let b = 0; for (let k = 0; k < 8; k++) if (r[i + k] === '1') b |= 0x80 >> k; out.push(b);
  }
  return Uint8Array.from(out);
}

/** frames: array of frames, each an array of 12 row strings. */
export function animation(frames, timeMs, spd, effectIdx) {
  const w = new Writer();
  record(w, 9, 11, x => x.u16(frames.length));
  for (const f of frames) {
    const bm = packRows(f);
    w.begin().u32(12 + bm.length).u16(0x60).u16(W).u16(H).u8(1).bytes(bm).checksum();
  }
  time(w, timeMs); speed(w, spd); effect(w, effectIdx);
  return w.out();
}

export function sendData(content, serial) {
  const w = new Writer();
  w.begin().u32(15).u16(SEND_DATA_TYPE).u32(serial).u32(content.length).checksum();
  w.bytes(content);
  return w.out();
}
export const startCmd = (serial, type, len) => new Writer().u8(10).u8(1).u16(serial).u16(type).u32(len).out();
export const finishCmd = (serial, type, len) => { const a = startCmd(serial, type, len); a[1] = 3; return a; };

const be16 = (b, o) => (b[o] << 8) | b[o + 1];
const be32 = (b, o) => be16(b, o) * 65536 + be16(b, o + 2);
export function parseResponse(raw) {
  let off;
  if (raw.length && raw[0] === raw.length) off = 0;
  else if (raw.length >= 5 && raw[3] === raw.length - 3) off = 3;
  else throw new Error('bad notification');
  const v = raw.slice(off), c = v.slice(2);
  switch (v[1]) {
    case 2: case 4: return { kind: 'sending', serial: be16(c, 0), error: c[2], type: be16(c, 3) };
    case 255: return { kind: 'continue', serial: be16(c, 0), type: be16(c, 2), from: be32(c, 4) };
    case 19: return { kind: 'display', width: be16(c, 3), height: be16(c, 5), frameLimit: c[8] };
    case 17: return { kind: 'version' };
    case 21: return { kind: 'buffer', size: be32(c, 3) };
    default: throw new Error('unknown response type ' + v[1]);
  }
}

// ---- font / text records (device-side custom text) ----
export function fontData(chars) { // chars: [{code,width,height,rows:[Uint8Array]}]
  const w = new Writer();
  record(w, 9, 5, x => x.u16(chars.length));
  for (const c of chars) {
    const bm = packRows(c.rows.map(r => [...r].join('')));
    w.begin().u32(15 + bm.length).u16(13).u8(1).u16(c.width).u16(c.height).u16(c.code).u8(bm.length).bytes(bm).checksum();
  }
  return w.out();
}
export function textData(text, spd, effectIdx) {
  const w = new Writer();
  record(w, 10, 4, x => x.u16(text.length).u8(1));
  for (const ch of text) { record(w, 10, 2, x => x.u8(255).u8(255).u8(255)); record(w, 9, 3, x => x.u16(ch.charCodeAt(0))); }
  time(w, 0); speed(w, spd); effect(w, effectIdx);
  return w.out();
}
