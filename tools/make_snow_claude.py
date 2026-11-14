import json, math, random, itertools
W, H, N = 48, 12, 20
SPR = {
 'A': ["...#...", ".#.#.#.", "..###..", "###.###", "..###..", ".#.#.#.", "...#..."],
 'B': ["..#..", "#.#.#", ".###.", "#.#.#", "..#.."],
 'C': [".#.", "###", ".#."],
 'D': ["#"],
}
# (sprite, x0, phase 0..1 of the fall cycle, k wraps over 20 frames, drift amplitude, drift phase, drift cycles)
def render(fl, t):
    g = [[0]*W for _ in range(H)]
    boxes = []
    for f in fl:
        s = SPR[f['s']]; h = len(s); w = len(s[0]); P = H + h
        top = round(((f['ph'] * P) + f['k'] * P * t / N) % P) - h   # -h .. H-1
        dx = round(f['amp'] * math.sin(2*math.pi*(f['cyc']*t/N + f['dph'])))
        x0 = f['x'] + dx
        boxes.append((x0, top, w, h))
        for y, row in enumerate(s):
            for x, c in enumerate(row):
                if c == '#' and 0 <= top+y < H and 0 <= x0+x < W:
                    g[top+y][x0+x] = 1
    return g, boxes

def ok(fl):
    for t in range(N):
        _, bx = render(fl, t)
        for (a, b) in itertools.combinations(bx, 2):
            # keep a clear gutter of 2 px around every flake (clipped parts count too)
            if a[0]-2 < b[0]+b[2] and b[0]-2 < a[0]+a[2] and a[1]-1 < b[1]+b[3] and b[1]-1 < a[1]+a[3]:
                return False
    return True

rnd = random.Random(7)
spec = [('A',1,1,1),('B',2,2,1),('C',3,1,1),('C',3,1,1),('D',3,0,0),('D',2,0,0),('D',3,0,0),('D',2,0,0)]
best = None
for _ in range(40000):
    order = spec[:]; rnd.shuffle(order)
    widths = [len(SPR[s][0]) for s, *_ in order]
    gap = (W - sum(widths)) / (len(order) + 1)
    fl = []; x = gap
    for (s, k, amp, cyc), w in zip(order, widths):
        fl.append(dict(s=s, k=k, amp=amp, cyc=cyc, dph=rnd.random(), ph=rnd.random(),
                       x=max(1, min(W - w - 1, int(round(x + rnd.uniform(-1.5, 1.5)))))))
        x += w + gap
    if not ok(fl): continue
    lit = [sum(map(sum, render(fl, t)[0])) for t in range(N)]
    score = (max(lit)-min(lit)) + abs(sum(lit)/N - 45)
    if best is None or score < best[0]: best = (score, fl, lit)
score, fl, lit = best
frames = [["".join(map(str, row)) for row in render(fl, t)[0]] for t in range(N)]
assert render(fl, N)[0] == render(fl, 0)[0]          # seamless loop
out = {"version":1,"tab":0,"current_frame":0,
       "image":{"frames":frames,"effect":"NONE","speed":150},
       "text":{"content":"","effect":"SCROLL_LEFT","speed":100,"two_lines":False}}
json.dump(out, open('snow_claude.json','w'), indent=1)
print("lit min/max/mean", min(lit), max(lit), sum(lit)/N)
