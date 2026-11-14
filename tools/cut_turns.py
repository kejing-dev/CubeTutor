#!/usr/bin/env python3
"""Cuts cube-turn clicks out of CC0 recordings (pure Python, no numpy).

  python3 -I tools/cut_turns.py --scan RAW.wav            # list onsets with peak / length / tail level
  python3 -I tools/cut_turns.py RAW.wav START_S OUT.wav [--max-ms 260]

Chain per clip: 1st-order high-pass 90 Hz, cut 4 ms before the onset, end where the envelope falls 42 dB under the peak
(max --max-ms), 10 ms cosine fade-out, linear gain to -1 dBFS peak. Mono 44.1 kHz 16-bit.
"""
import array, math, sys, wave

def read(path):
    w = wave.open(path, "rb")
    n, sr, ch, sw = w.getnframes(), w.getframerate(), w.getnchannels(), w.getsampwidth()
    assert sw == 2, "16-bit only"
    a = array.array("h", w.readframes(n))
    if ch > 1:
        a = array.array("h", [int(sum(a[i:i + ch]) / ch) for i in range(0, len(a), ch)])
    return [x / 32768.0 for x in a], sr

def highpass(x, sr, fc=90.0):
    rc = 1.0 / (2 * math.pi * fc); dt = 1.0 / sr; al = rc / (rc + dt)
    y = [0.0] * len(x); prev_y = 0.0; prev_x = 0.0
    for i, v in enumerate(x):
        prev_y = al * (prev_y + v - prev_x); prev_x = v; y[i] = prev_y
    return y

def env(x, sr, ms=3):
    n = max(1, int(sr * ms / 1000)); out = []
    for i in range(0, len(x), n):
        out.append(max(abs(v) for v in x[i:i + n]))
    return out, n

def onsets(x, sr):
    e, hop = env(x, sr)
    floor = sorted(e)[len(e) // 5] + 1e-6
    res = []; last = -10**9
    for i in range(1, len(e)):
        if e[i] > floor * 12 and e[i] > 2.5 * max(e[max(0, i - 8):i]) and (i - last) * hop > sr * 0.12:
            res.append(i * hop); last = i
    return res, floor

def cut(x, sr, start, max_ms=260):
    s = max(0, start - int(sr * 0.004))
    seg = x[s:s + int(sr * max_ms / 1000)]
    peak = max(abs(v) for v in seg) or 1e-9
    e, hop = env(seg, sr, 2)
    pi = max(range(len(e)), key=lambda i: e[i])
    end = len(seg)
    for i in range(pi, len(e)):
        if e[i] < peak * 10 ** (-42 / 20):
            end = min(len(seg), (i + 1) * hop); break
    seg = seg[:end]
    fade = int(sr * 0.010)
    for i in range(min(fade, len(seg))):
        seg[len(seg) - 1 - i] *= 0.5 - 0.5 * math.cos(math.pi * i / fade)
    g = 10 ** (-1 / 20) / max(abs(v) for v in seg)
    return [v * g for v in seg], peak

def write(path, x, sr):
    w = wave.open(path, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
    w.writeframes(array.array("h", [max(-32767, min(32767, int(v * 32767))) for v in x]).tobytes()); w.close()

def stats(x):
    rms = math.sqrt(sum(v * v for v in x) / max(1, len(x)))
    return 20 * math.log10(rms + 1e-12), 20 * math.log10(max(abs(v) for v in x) + 1e-12)

if __name__ == "__main__":
    a = sys.argv[1:]
    if a[0] == "--scan":
        x, sr = read(a[1]); x = highpass(x, sr)
        on, floor = onsets(x, sr)
        print(f"sr={sr} len={len(x)/sr:.2f}s floor={20*math.log10(floor):.1f} dB onsets={len(on)}")
        for o in on[:80]:
            seg, peak = cut(x, sr, o)
            r, p = stats(seg)
            print(f"  t={o/sr:7.3f}s peak={20*math.log10(peak):6.1f} dBFS len={len(seg)*1000//sr:4d} ms rms={r:6.1f}")
    else:
        x, sr = read(a[0]); x = highpass(x, sr)
        mx = int(a[a.index("--max-ms") + 1]) if "--max-ms" in a else 260
        seg, _ = cut(x, sr, int(float(a[1]) * sr), mx)
        write(a[2], seg, sr)
        r, p = stats(seg)
        print(f"{a[2]} {len(seg)*1000//sr} ms RMS {r:.1f} dBFS peak {p:.1f} dBFS")
