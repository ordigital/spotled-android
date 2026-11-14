import json, math
W, H, N = 48, 12, 20

def blank(): return [[0]*W for _ in range(H)]
def put(g, spr, x0, y0, on='#', clear=False):
    for y, row in enumerate(spr):
        for x, c in enumerate(row):
            X, Y = x0+x, y0+y
            if 0 <= X < W and 0 <= Y < H:
                if c == on: g[Y][X] = 1
                elif clear and c == '.': g[Y][X] = 0
def rows(g): return ["".join(map(str, r)) for r in g]
def save(name, frames, speed):
    assert len(frames) == N and all(len(f) == H and all(len(r) == W for r in f) for f in frames)
    json.dump({"version":1,"tab":0,"current_frame":0,
               "image":{"frames":frames,"effect":"NONE","speed":speed},
               "text":{"content":"","effect":"SCROLL_LEFT","speed":100,"two_lines":False}},
              open(name, 'w'), indent=1)

# ---------- 5x7 font ----------
F = {
 'W': ["#...#","#...#","#...#","#.#.#","#.#.#","##.##","#...#"],
 'A': [".###.","#...#","#...#","#####","#...#","#...#","#...#"],
 'R': ["####.","#...#","#...#","####.","#.#..","#..#.","#...#"],
 'N': ["#...#","##..#","#.#.#","#.#.#","#..##","#...#","#...#"],
 'I': ["#####","..#..","..#..","..#..","..#..","..#..","#####"],
 'G': [".###.","#...#","#....","#.###","#...#","#...#",".###."],
 'E': ["#####","#....","#....","####.","#....","#....","#####"],
 'T': ["#####","..#..","..#..","..#..","..#..","..#..","..#.."],
 'O': [".###.","#...#","#...#","#...#","#...#","#...#",".###."],
}
def text_sprite(s, gap=1):
    out = [""]*7
    for i, ch in enumerate(s):
        for r in range(7): out[r] += F[ch][r] + ("." * gap if i < len(s)-1 else "")
    return out

# ================= WARNING =================
def triangle(filled):
    w, h = 11, 10
    g = [['.']*w for _ in range(h)]
    for r in range(h-1):
        l = 5 - (r+1)//2; rr = 10 - l
        g[r][l] = g[r][rr] = '#'
        if filled:
            for x in range(l, rr+1): g[r][x] = '#'
    for x in range(w): g[h-1][x] = '#'
    exclam = [(5, r) for r in range(3, 7)] + [(5, 8)]
    for x, y in exclam: g[y][x] = '.' if filled else '#'
    return ["".join(r) for r in g]
TRI = {True: triangle(True), False: triangle(False)}
WARN = text_sprite("WARNING")          # 41 x 7
warn = []
for t in range(N):
    g = blank()
    if t < 10:                          # scene A: wave of three triangles
        for j, x0 in enumerate((4, 18, 33)):
            put(g, TRI[((t - 2*j) % 6) < 3], x0, 1)
    else:                               # scene B: blinking WARNING between marching hazard dashes
        k = t - 10
        if k not in (2, 5):             # two short blackouts = flash
            put(g, WARN, 3, 2)
        for x in range(W):
            if (x + k) % 4 < 2: g[0][x] = 1
            if (x - k) % 4 < 2: g[11][x] = 1
    warn.append(rows(g))
save('warning_claude.json', warn, 120)

# ================= EAGLE =================
EAGLE = [
 "#.#...#.#...#.#",
 "#.#...###...#.#",
 "###..#####..###",
 "####.######.###",
 ".####.###.####.",
 "..###.###.###..",
 "...###.#.###...",
 "....##.#.##....",
 ".....#####.....",
 "......###......",
 "......#.#......",
 ".....##.##.....",
]
assert all(len(r) == 15 for r in EAGLE) and len(EAGLE) == 12
EAGLE = [list(r) for r in EAGLE]
EAGLE[3][11] = '#'                      # beak: head faces right
EAGLE = ["".join(r) for r in EAGLE]
eagle = []
for t in range(N):
    g = blank()
    top = 11 - round(22 * t / 19)
    put(g, EAGLE, 17, top)
    eagle.append(rows(g))
save('eagle_claude.json', eagle, 100)

# ================= I <3 RETRO =================
HEART_BIG = [".##.##.","#######","#######",".#####.","..###..","...#..."]
HEART_SM  = [".#.#.","#####",".###.","..#.."]
INV = [[
 "..#.....#..","...#...#...","..#######..",".##.###.##.","###########","#.#######.#","#.#.....#.#","...##.##...",
],[
 "..#.....#..","#..#...#..#","#.#######.#","###.###.###","###########",".#########.","..#.....#..",".#.......#.",
]]
def pac(mouth):
    g = [['.']*7 for _ in range(7)]
    for y in range(7):
        for x in range(7):
            dx, dy = x-3, y-3
            if dx*dx + dy*dy <= 11.5:
                ang = abs(math.degrees(math.atan2(dy, dx))) if dx > 0 or dy != 0 else 0
                if dx > 0 and ang < mouth: continue
                g[y][x] = '#'
    return ["".join(r) for r in g]
PAC = [pac(0), pac(28), pac(50), pac(28)]
GHOST = [[
 "..###..",".#####.","#######","##.#.##","#######","#######","#.#.#.#"],[
 "..###..",".#####.","#######","##.#.##","#######","#######",".#.#.#."]]
RETRO_TXT = text_sprite("I", 1)   # we compose manually below
def line(*parts, gap=2):
    h = max(len(p) for p in parts); out = [""]*h
    for i, p in enumerate(parts):
        pad = [""]*(h-len(p)) ; w = len(p[0])
        for r in range(h):
            row = p[r - (h - len(p))] if r >= h - len(p) else "." * w
            out[r] += row + ("." * gap if i < len(parts)-1 else "")
    return out
retro = []
word = text_sprite("RETRO", 1)
for t in range(N):
    g = blank()
    if t < 8:                           # scene 1: I <3 RETRO with pulsing heart and arcade marquee
        heart = HEART_BIG if (t // 2) % 2 == 0 else HEART_SM
        hp = [r.center(7, '.') for r in heart]
        hp = ["......."]*((6-len(hp))//1 if False else 0) + hp
        x = 1
        put(g, F['I'], x, 2); x += 5 + 2
        put(g, heart, x + (7-len(heart[0]))//2, 2 + (6-len(heart))//2); x += 7 + 2
        put(g, word, x, 2)
        for xx in range(W):
            if (xx + t) % 3 == 0: g[0][xx] = 1
            if (xx - t) % 3 == 0: g[11][xx] = 1
    elif t < 16:                        # scene 2: Pac-Man eats the dots, ghost chases
        k = t - 8
        px = -7 + round(k * (W + 22) / 7)
        for dx in range(2, W, 4):
            if dx > px + 6: g[5][dx] = 1
        put(g, PAC[k % 4], px, 2, clear=False)
        put(g, GHOST[k % 2], px - 11, 2)
    else:                               # scene 3: two space invaders dancing
        k = t - 16
        pose = INV[k % 2]
        put(g, pose, 9, 2); put(g, pose, 28, 2)
    retro.append(rows(g))
save('retro_claude.json', retro, 120)

def show(name, fs, ts):
    print("==", name)
    for t in ts:
        print("frame", t)
        for r in fs[t]: print(" ", r.replace('0','.').replace('1','#'))
show('warning', warn, (0, 3, 10, 12))
show('eagle', eagle, (6, 9, 13))
show('retro', retro, (0, 2, 11, 17))
