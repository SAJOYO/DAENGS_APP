#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""채소를 VectorDrawable 로 만든다.

카드의 큰 그림창에 들어가는 것은 "강아지 얼굴이 채소 안에 들어간" 모습이다
(`app/src/main/assets/neo-hologram/art/cabbage-subject.webp` 가 원본 개념).

**PNG 로 받지 않는다.** 채소를 늘릴 때마다 그림을 요청해야 하고 APK 도 그만큼
커진다. 대신 `<vector>` XML 로 만든다 — 라이브러리가 필요 없고(안드로이드 기본),
파일은 몇 KB 이며, 확대해도 안 깨진다. 채소 하나를 늘리는 일이 이 스크립트에
줄 하나를 더하는 일이 된다.

## 왜 스크립트로 만드나

잎을 코틀린에서 sin/cos 로 그려 봤는데 종이 오린 것처럼 나왔다. 문제는 어디서
도느냐가 아니라 **잎 모양 자체**였다. 그래서 잎 하나를 베지에로 제대로 그려 두고
(`LEAF`), 그 제어점을 회전·크기 조절해서 배치한다 — 벡터 일러스트가 원래 하는
방식이다. 점을 찍어 다각형으로 잇는 것과 달리 윤곽이 매끄럽다.

## 앞잎과 뒷잎을 나눈다

`<파일>_back.xml` 과 `<파일>_front.xml` 두 장을 낸다. 앱은 **뒷잎 → 얼굴 → 앞잎**
순서로 그려서 잎이 얼굴 가장자리를 덮게 한다. 그림 한 장이면 앞뒤를 못 나눠서
얼굴을 늘 잎 위에 얹어야 하고, 그러면 붙여 놓은 것으로 보인다.

    uv run tools/make_veggie_vector.py
"""

from __future__ import annotations

import math
import pathlib
import random

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"

# 그리는 상자. VectorDrawable 의 viewport 이고, 화면 크기와는 무관하다.
SIZE = 512.0
CENTER = SIZE / 2

# ---------------------------------------------------------------------------
# 잎 한 장.
#
# 밑동(0,0)에서 끝(0,-100)까지, 좌우로 ±46 까지 벌어진다. **가장 넓은 데가 한가운데가
# 아니라 6할쯤 위**다 — 한가운데가 제일 넓으면 잎이 아니라 마름모로 보인다.
# 위쪽 가장자리에 얕은 굴곡을 둬서 배추처럼 구겨진 느낌을 낸다.
#
# 각 항목은 삼차 베지에 한 구간이다: (제어점1, 제어점2, 끝점).
# ---------------------------------------------------------------------------
LEAF: list[tuple[tuple[float, float], tuple[float, float], tuple[float, float]]] = [
    ((-16, -6), (-34, -22), (-44, -46)),
    ((-53, -68), (-46, -86), (-30, -94)),
    ((-22, -98), (-19, -90), (-13, -95)),
    ((-8, -99), (-5, -93), (0, -100)),
    ((5, -93), (8, -99), (13, -95)),
    ((19, -90), (22, -98), (30, -94)),
    ((46, -86), (53, -68), (44, -46)),
    ((34, -22), (16, -6), (0, 0)),
]


def leaf_path(cx: float, cy: float, angle: float, length: float, width: float) -> str:
    """잎 하나를 절대 좌표 경로 문자열로. [angle] 은 도(度), 0이 12시 방향."""
    rad = math.radians(angle)
    sin_a, cos_a = math.sin(rad), math.cos(rad)

    def at(p: tuple[float, float]) -> str:
        # 잎 좌표계는 위(-y)가 끝이다. 폭과 길이를 따로 줘서 통통하거나 길쭉하게 만든다.
        x = p[0] / 46.0 * width
        y = p[1] / 100.0 * length
        return f"{cx + x * cos_a - y * sin_a:.1f},{cy + x * sin_a + y * cos_a:.1f}"

    parts = [f"M{at((0, 0))}"]
    for c1, c2, end in LEAF:
        parts.append(f"C{at(c1)} {at(c2)} {at(end)}")
    parts.append("Z")
    return "".join(parts)


def ring(
    rng: random.Random,
    count: int,
    length: float,
    width: float,
    offset: float,
    fill: str,
    stroke: str,
) -> list[str]:
    """겹 하나. 잎을 [count] 장 돌려 놓는다."""
    out = []
    step = 360.0 / count
    for i in range(count):
        angle = offset + step * i + rng.uniform(-step * 0.10, step * 0.10)
        # 잎마다 조금씩 다르게. 전부 같으면 바퀴살처럼 보인다.
        d = leaf_path(
            CENTER,
            CENTER,
            angle,
            length * rng.uniform(0.93, 1.07),
            width * rng.uniform(0.90, 1.10),
        )
        out.append(
            f'    <path\n'
            f'        android:pathData="{d}"\n'
            f'        android:fillColor="{fill}"\n'
            f'        android:strokeColor="{stroke}"\n'
            f'        android:strokeWidth="1.6"\n'
            f'        android:strokeAlpha="0.45" />'
        )
    return out


def vector(body: list[str]) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- tools/make_veggie_vector.py 가 만든 파일이다. 직접 고치지 말 것. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="256dp"\n'
        f'    android:height="256dp"\n'
        f'    android:viewportWidth="{SIZE:.0f}"\n'
        f'    android:viewportHeight="{SIZE:.0f}">\n'
        + "\n".join(body)
        + "\n</vector>\n"
    )


def cabbage() -> tuple[str, str]:
    """배추. 뒷잎 세 겹과 앞잎 한 겹."""
    rng = random.Random(20260829)

    back: list[str] = []
    # 바깥이 크고 진하다. 안으로 갈수록 짧고 밝아진다 — 배추 속이 연한 것과 같다.
    back += ring(rng, 9, 236, 62, 0, "#FF2E5A1B", "#FF1D3C10")
    back += ring(rng, 9, 196, 56, 20, "#FF3F7423", "#FF2A4F17")
    back += ring(rng, 8, 158, 50, 42, "#FF579330", "#FF3B6B20")

    # 앞잎은 얼굴 **아래쪽**을 주로 덮는다. 위를 덮으면 눈이 가린다.
    front: list[str] = []
    rng_f = random.Random(7717)
    step = 360.0 / 7
    for i in range(7):
        # 6시 방향(180도) 언저리에 몰아 둔다.
        angle = 180 + (i - 3) * step * 0.62 + rng_f.uniform(-6, 6)
        d = leaf_path(CENTER, CENTER, angle, 124 * rng_f.uniform(0.92, 1.08), 44)
        front.append(
            f'    <path\n'
            f'        android:pathData="{d}"\n'
            f'        android:fillColor="#FF77B23C"\n'
            f'        android:strokeColor="#FF4E7A28"\n'
            f'        android:strokeWidth="1.6"\n'
            f'        android:strokeAlpha="0.45" />'
        )
    # 위쪽에도 두 장만. 얼굴이 잎 사이에 끼었다는 느낌은 위아래가 다 있어야 난다.
    for angle in (-32, 30):
        d = leaf_path(CENTER, CENTER, angle, 104, 40)
        front.append(
            f'    <path\n'
            f'        android:pathData="{d}"\n'
            f'        android:fillColor="#FF8CC24C"\n'
            f'        android:strokeColor="#FF4E7A28"\n'
            f'        android:strokeWidth="1.6"\n'
            f'        android:strokeAlpha="0.45" />'
        )

    return vector(back), vector(front)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    back, front = cabbage()
    (OUT / "veggie_cabbage_back.xml").write_text(back, encoding="utf-8")
    (OUT / "veggie_cabbage_front.xml").write_text(front, encoding="utf-8")
    for name in ("veggie_cabbage_back.xml", "veggie_cabbage_front.xml"):
        path = OUT / name
        print(f"{name}  {path.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
