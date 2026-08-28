# /// script
# requires-python = ">=3.11"
# dependencies = ["soundfile>=0.12", "numpy>=1.26"]
# ///
"""MP3 → OGG(Vorbis). 이머시브 배경음을 앱 에셋으로 옮길 때 쓴다.

    uv run tools/convert_audio.py <입력.mp3> <출력.ogg>

## 왜 OGG 인가

**MP3 는 이어 붙이면 틈이 생긴다.** 인코더가 앞뒤에 프레임 몇 개를 채워 넣는데
(encoder delay/padding), 그 값이 파일 안에 있어도 `MediaPlayer` 의 반복 재생은
그걸 안 걷어낸다. 곡이 29초라 30초마다 한 번씩 "툭" 이 들린다.

Vorbis 는 샘플 단위로 길이가 정확해서 이어 붙는다.

## 왜 ffmpeg 이 아닌가

이 PC 에 없다. `soundfile` 이 들고 오는 libsndfile 이 1.1 부터 MP3 를 읽고
Vorbis 를 쓸 수 있어서, 팀 규칙대로 PEP 723 + `uv run` 한 줄로 끝난다
(`pip` 을 쓰지 않는다 — CLAUDE.md).
"""

from __future__ import annotations

import sys
from pathlib import Path

import soundfile as sf


# 한 번에 넘길 프레임 수.
#
# ⚠️ **통째로 넘기면 죽는다.** libsndfile 1.2.2 의 Vorbis 인코더에 29초(128만 프레임)를
#    한 번에 주면 윈도우에서 스택 오버플로(0xC00000FD)로 프로세스가 그대로 사라진다 —
#    예외도 없고 stderr 도 비어 있어서, 4KB 짜리 머리만 있는 ogg 가 남는다.
#    1초짜리로는 멀쩡해서 짧게 시험하면 안 보인다.
BLOCK = 1 << 16


def convert(src: Path, dst: Path) -> None:
    data, rate = sf.read(str(src), always_2d=True)
    dst.parent.mkdir(parents=True, exist_ok=True)
    with sf.SoundFile(
        str(dst), "w",
        samplerate=rate,
        channels=data.shape[1],
        format="OGG",
        subtype="VORBIS",
    ) as out:
        for i in range(0, len(data), BLOCK):
            out.write(data[i:i + BLOCK])

    seconds = len(data) / rate
    print(f"{src.name} ({src.stat().st_size / 1024:.0f}KB)")
    print(f"  -> {dst.name} ({dst.stat().st_size / 1024:.0f}KB) "
          f"· {seconds:.1f}초 · {rate}Hz · {data.shape[1]}채널")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    src, dst = Path(argv[0]), Path(argv[1])
    if not src.exists():
        print(f"입력 파일이 없습니다: {src}")
        return 1
    convert(src, dst)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
