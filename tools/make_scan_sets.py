#!/usr/bin/env python3
"""Synthetic "photo" sets for the cube reader (design emulatorVerification.assets ①): pure Python, no PIL / Blender.

Each set is six 640 x 480 PNGs face1..face6 in the read order (F R B L U D, cubeSpec.readOrder) of one scrambled cube,
as the right-eye camera would see a 57 mm cube held ~0.30 m away (90° horizontal field of view): a slight perspective
tilt, a random roll, warm / cool / neutral light, sensor noise, a glare spot, and a cluttered background with
saturated patches. Facelets are drawn row by row as cubeSpec.readOrder says (U with B on top, D with F on top);
--turn-u / --turn-d draw U / D turned (the reader must fix it).

  python3 -I tools/make_scan_sets.py OUTDIR            # setA (warm), setB (cool), setC (neutral, clutter), setBad
"""
import math
import os
import random
import struct
import sys
import zlib

W, H = 640, 480
FOCAL = (W / 2) / math.tan(math.radians(45))
CUBE = 0.057
CELL = CUBE / 3
STICKER = 0.0165

TRUE = {
    "W": (232, 234, 232),
    "Y": (250, 214, 10),
    "R": (196, 22, 34),
    "O": (255, 112, 10),
    "B": (10, 72, 196),
    "G": (10, 158, 64),
}
LIGHT = {"warm": (1.0, 0.88, 0.70, 0.95), "cool": (0.86, 0.95, 1.08, 0.9), "neutral": (1.0, 1.0, 1.0, 0.85)}

# codes from the app's own Scrambler (PrintCodesTest, seeds 11 / 22 / 33), URFDLB
CODES = {
    "setA": "OBRWYOGYWOYYGGORBRYGBORRBYBOBWWWORBWWRORBGGGYBRGWOYGWY",
    "setB": "ROOOYYOGBOOYGGBBWBWWYYRRYGWGORBWBYROGWGYBGBRRGBWYORWWR",
    "setC": "RBYGYYBRRWGBYGORWYWGBORROGWGWGBWBWROYOOOBYORYRYGWOBBWG",
}
READ_FACES = [2, 1, 5, 4, 0, 3]  # Lessons.readFaces


def png(path, rgb):
    raw = b"".join(b"\x00" + bytes(rgb[y * W * 3:(y + 1) * W * 3]) for y in range(H))

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def rot(ax, ay, az):
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    return mul(rz, mul(ry, rx))


def turned(face9, t):
    """The picture of a face turned t quarter turns clockwise."""
    g = [face9[i * 3:i * 3 + 3] for i in range(3)]
    for _ in range(t % 4):
        g = [[g[2 - c][r] for c in range(3)] for r in range(3)]
    return [x for row in g for x in row]


def render(face9, light, rnd, clutter):
    lr, lg, lb, gain = LIGHT[light]
    img = bytearray(W * H * 3)
    # background: dim room + clutter patches
    base = (rnd.randint(70, 110), rnd.randint(65, 100), rnd.randint(55, 95))
    for i in range(W * H):
        img[i * 3:i * 3 + 3] = bytes(base)
    for _ in range(clutter):
        x0, y0 = rnd.randint(0, W - 40), rnd.randint(0, H - 40)
        w, h = rnd.randint(20, 160), rnd.randint(20, 120)
        col = rnd.choice(list(TRUE.values()) + [(120, 90, 60), (200, 170, 140), (40, 40, 40)])
        col = tuple(int(c * 0.7) for c in col)
        for y in range(y0, min(H, y0 + h)):
            for x in range(x0, min(W, x0 + w)):
                img[(y * W + x) * 3:(y * W + x) * 3 + 3] = bytes(col)
    # the face: a 3-D square ~0.3 m ahead, tilted
    dist = rnd.uniform(0.27, 0.32)
    R = rot(math.radians(rnd.uniform(-12, 12)), math.radians(rnd.uniform(-12, 12)), math.radians(rnd.uniform(-18, 18)))
    cx = rnd.uniform(-0.025, 0.025)
    cy = rnd.uniform(-0.02, 0.02)
    origin = (cx, cy, dist)
    ex = (R[0][0], R[1][0], R[2][0])  # face right
    ey = (R[0][1], R[1][1], R[2][1])  # face down (image y grows down)
    n = (R[0][2], R[1][2], R[2][2])
    # hand-ish skin blob behind / around the cube
    sx0 = int(W / 2 + cx / dist * FOCAL)
    sy0 = int(H / 2 + cy / dist * FOCAL)
    for y in range(max(0, sy0 + 10), min(H, sy0 + 120)):
        for x in range(max(0, sx0 - 70), min(W, sx0 + 70)):
            img[(y * W + x) * 3:(y * W + x) * 3 + 3] = bytes((196, 150, 120))
    half = CUBE / 2 + 0.004
    glare = rnd.randrange(9)
    span = int(FOCAL * (CUBE + 0.02) / dist)
    for y in range(max(0, sy0 - span), min(H, sy0 + span)):
        for x in range(max(0, sx0 - span), min(W, sx0 + span)):
            d = ((x - W / 2) / FOCAL, (y - H / 2) / FOCAL, 1.0)
            den = d[0] * n[0] + d[1] * n[1] + d[2] * n[2]
            if abs(den) < 1e-6:
                continue
            t = (origin[0] * n[0] + origin[1] * n[1] + origin[2] * n[2]) / den
            p = (d[0] * t - origin[0], d[1] * t - origin[1], d[2] * t - origin[2])
            u = p[0] * ex[0] + p[1] * ex[1] + p[2] * ex[2]
            v = p[0] * ey[0] + p[1] * ey[1] + p[2] * ey[2]
            if abs(u) > half or abs(v) > half:
                continue
            col = (18, 18, 20)
            if abs(u) < CUBE / 2 and abs(v) < CUBE / 2:
                ci = min(2, int((u + CUBE / 2) / CELL))
                ri = min(2, int((v + CUBE / 2) / CELL))
                lu = u + CUBE / 2 - (ci + 0.5) * CELL
                lv = v + CUBE / 2 - (ri + 0.5) * CELL
                if abs(lu) < STICKER / 2 and abs(lv) < STICKER / 2:
                    col = TRUE[face9[ri * 3 + ci]]
                    k = ri * 3 + ci
                    if k == glare and lu > 0 and lv < 0 and lu * lu + lv * lv < (STICKER / 4) ** 2:
                        col = (255, 255, 255)
            img[(y * W + x) * 3:(y * W + x) * 3 + 3] = bytes(col)
    # light + noise
    for i in range(0, W * H * 3, 3):
        r, g, b = img[i], img[i + 1], img[i + 2]
        img[i] = max(0, min(255, int(r * lr * gain + rnd.gauss(0, 4))))
        img[i + 1] = max(0, min(255, int(g * lg * gain + rnd.gauss(0, 4))))
        img[i + 2] = max(0, min(255, int(b * lb * gain + rnd.gauss(0, 4))))
    return img


def make_set(out, name, code, light, seed, clutter, turn_u=0, turn_d=0):
    rnd = random.Random(seed)
    d = os.path.join(out, name)
    os.makedirs(d, exist_ok=True)
    for n, f in enumerate(READ_FACES, 1):
        face9 = list(code[f * 9:f * 9 + 9])
        if f == 0:
            face9 = turned(face9, turn_u)
        if f == 3:
            face9 = turned(face9, turn_d)
        png(os.path.join(d, "face%d.png" % n), render(face9, light, rnd, clutter))
    with open(os.path.join(d, "truth.txt"), "w") as fh:
        fh.write(code + "\n")
    print("wrote", d)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/debug/assets/scan"
    make_set(out, "setA", CODES["setA"], "warm", 1, 6, turn_u=1, turn_d=2)
    make_set(out, "setB", CODES["setB"], "cool", 2, 10)
    make_set(out, "setC", CODES["setC"], "neutral", 3, 18)
    # a bad sample: one corner sticker of the first face painted the wrong colour (count check fails → S07)
    bad = list(CODES["setA"])
    bad[18] = "W" if bad[18] != "W" else "Y"
    make_set(out, "setBad", "".join(bad), "neutral", 4, 6)


if __name__ == "__main__":
    main()
