#!/usr/bin/env python3
"""Renders the app's event sounds at the default gains (master 80 % × each event's gain in platform/Sound.kt) one after
another into one WAV (build.md: short-cue apps measure the sounding parts; 渲染，非录音 — the emulator's audio cannot be
recorded here). Prints RMS / peak like ui_gates (20 ms windows above peak − 30 dB).
  python3 -I tools/render_loudness.py OUT.wav
"""
import array, math, os, sys, wave
HERE = os.path.dirname(os.path.abspath(__file__))
SFX = os.path.join(HERE, "..", "app", "src", "main", "assets", "sfx")
MASTER = 0.80
EVENTS = [("turn", 1.0), ("turn_2", 1.0), ("turn_3", 1.0), ("whole", 0.9), ("select", 0.7), ("correct", 0.8), ("nudge", 0.7), ("stage", 0.8), ("solved", 0.85)]
def read(p):
    w = wave.open(p, "rb"); sr = w.getframerate(); ch = w.getnchannels()
    a = array.array("h", w.readframes(w.getnframes()))
    if ch > 1: a = array.array("h", [int(sum(a[i:i + ch]) / ch) for i in range(0, len(a), ch)])
    return [x / 32768 for x in a], sr
out = []; SR = 44100
for _ in range(3):
    for name, g in EVENTS:
        x, sr = read(os.path.join(SFX, name + ".wav"))
        if sr != SR:
            x = [x[int(i * sr / SR)] for i in range(int(len(x) * SR / sr))]
        out += [v * g * MASTER for v in x] + [0.0] * int(SR * 0.12)
w = wave.open(sys.argv[1], "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
w.writeframes(array.array("h", [int(max(-1, min(1, v)) * 32767) for v in out]).tobytes()); w.close()
peak = max(abs(v) for v in out); n = int(SR * 0.02)
wins = [math.sqrt(sum(v * v for v in out[i:i + n]) / n) for i in range(0, len(out) - n, n)]
loud = [r for r in wins if r > peak * 10 ** (-30 / 20)]
rms = math.sqrt(sum(r * r for r in loud) / len(loud))
print(f"{sys.argv[1]} {len(out)/SR:.1f} s peak {20*math.log10(peak):.1f} dBFS RMS(sounding) {20*math.log10(rms):.1f} dBFS")
