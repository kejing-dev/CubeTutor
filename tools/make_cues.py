#!/usr/bin/env python3
"""UI-only cues (sound-sources: 合成（UI）): stage.wav three rising notes, solved.wav a 1.2 s arpeggio.
Soft bell timbre (sine + 2nd / 3rd partials, exponential decay), peak -1 dBFS, mono 44.1 kHz.
  python3 -I tools/make_cues.py app/src/main/assets/sfx
"""
import array, math, sys, wave
SR = 44100

def note(f, dur, amp=1.0):
    n = int(SR * dur); out = []
    for i in range(n):
        t = i / SR
        e = min(1.0, t / 0.004) * math.exp(-t * 5.5)
        s = math.sin(2 * math.pi * f * t) + 0.35 * math.sin(2 * math.pi * 2 * f * t) * math.exp(-t * 4) + 0.12 * math.sin(2 * math.pi * 3 * f * t) * math.exp(-t * 8)
        out.append(amp * e * s)
    return out

def mix(parts, total):
    buf = [0.0] * int(SR * total)
    for start, x in parts:
        o = int(SR * start)
        for i, v in enumerate(x):
            if o + i < len(buf): buf[o + i] += v
    g = 10 ** (-1 / 20) / max(abs(v) for v in buf)
    fade = int(SR * 0.03)
    for i in range(fade): buf[-1 - i] *= i / fade
    return [v * g for v in buf]

def write(path, x):
    w = wave.open(path, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(array.array("h", [int(max(-1, min(1, v)) * 32767) for v in x]).tobytes()); w.close()
    rms = 20 * math.log10(math.sqrt(sum(v * v for v in x) / len(x)))
    print(path, f"{len(x) * 1000 // SR} ms RMS {rms:.1f} dBFS")

if __name__ == "__main__":
    d = sys.argv[1]
    c5, e5, g5, c6, e6 = 523.25, 659.25, 783.99, 1046.5, 1318.5
    write(f"{d}/stage.wav", mix([(0.0, note(c5, 0.5)), (0.12, note(e5, 0.5)), (0.24, note(g5, 0.6))], 0.85))
    write(f"{d}/solved.wav", mix([(0.0, note(c5, 0.7)), (0.11, note(e5, 0.7)), (0.22, note(g5, 0.7)), (0.33, note(c6, 0.8)), (0.46, note(e6, 0.74))], 1.2))
