import * as P from './protocol.js';
const hex = s => Uint8Array.from(s.replace(/\s/g, '').match(/../g).map(x => parseInt(x, 16)));
const blank = Array(12).fill('0'.repeat(48));
const got = P.animation([blank], 100, 0, 0);
const exp = Uint8Array.from([...hex('00000009000b000115 00000054 0060 0030 000c 01'), ...new Uint8Array(72), ...hex('f1 0000000a0007000064 75 0000000800090011 0000000800080010')]);
console.log(got.length === exp.length && got.every((x, i) => x === exp[i]) ? 'animation vector OK' : 'animation vector MISMATCH');
const body = Uint8Array.of(0,0,0,8,0,14,50); 
const env = P.sendData(hex('00000008000e3248'), 1);
console.log(Buffer.from(env).toString('hex'));
