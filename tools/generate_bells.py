#!/usr/bin/env python3
"""Reproduce the original CC0 two-second bell placeholders using only Python's standard library."""
from pathlib import Path
import math
import struct
import wave

OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
for name, frequency in (("start_bell", 440), ("completion_bell", 523.25)):
    with wave.open(str(OUTPUT / f"{name}.wav"), "wb") as bell:
        bell.setparams((1, 2, 22050, 0, "NONE", "not compressed"))
        frames = []
        for sample in range(44100):
            t = sample / 22050
            envelope = (1 - math.exp(-t * 40)) * math.exp(-t * 2.6)
            tone = math.sin(2 * math.pi * frequency * t) + .22 * math.sin(2 * math.pi * frequency * 2.76 * t)
            frames.append(struct.pack("<h", int(7200 * envelope * tone / 1.22)))
        bell.writeframes(b"".join(frames))
