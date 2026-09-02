#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10", "numpy>=1.26"]
# ///
"""저쪽에서 온 카드에서 **강아지 이름이 들어갈 칸**을 찾아 깨끗이 비운다.

    uv run tools/name_slot.py            # 미리 보기 (파일 안 건드림)
    uv run tools/name_slot.py --apply    # 고치고 좌표를 찍는다

## 저쪽 카드는 `SPINACH NEO` 다

야채 이름 + 강아지 이름이고, 뒷말이 우리가 갈아 끼울 자리다. `121e6a3` 으로 들어온
열 장은 그 뒷말을 **모자이크로 뭉개** 놓았을 뿐이라, 카드에 지우다 만 자국이 그대로
보인다. 배추·고구마 두 장만 `punch_card_slots.py` 로 제대로 비워져 있다.

## 뭉갠 자국은 기계로 찾을 수 있다

모자이크는 **8픽셀 블록이 통째로 한 색**이다. 그림에는 그런 자리가 없다 — 홀로그램
무늬든 글자든 블록 안에서 색이 흔들린다. 조각으로 흩어져 잡히므로 묶는다.

**번호판 자국과 섞이면 안 된다.** 둘 다 위쪽 띠에 있고 x 범위도 겹친다(단호박의
이름칸과 토마토의 번호판이 같은 자리다). 가르는 것은 **바탕색**이다 — 이름은 어두운
제목 바 위에 있고 번호판은 밝은 육각 무늬 판 위에 있다. 조각 바로 왼쪽을 찍어 보면
갈린다.

## 메우는 방향

**왼쪽 바 색을 오른쪽으로 늘인다.** 처음에는 좌우 끝을 이었는데, 오른쪽 끝이 바
바깥의 노란 판이라 빈 자리가 노랗게 번졌다. 끝 15% 만 원래 색으로 되돌려 이음매를
지운다.

## 번호판 자국은 못 고친다

여기서는 **이름칸만** 고친다. 번호판 자국은 옆이 육각 무늬와 잎 아이콘이라 어떻게
메워도 얼룩이 남는다 — 가로로 이으면 노란 띠, 세로로 이으면 줄무늬, 아래를 복제하면
이음매가 보인다. 그건 저쪽에서 제대로 뚫은 판을 받아야 한다.
"""

from __future__ import annotations

import json
import pathlib
import sys
from collections import deque

import numpy as np
from PIL import Image

ART = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/neo-hologram/art"

# 배추·고구마는 이미 제대로 비워져 있다.
SKIP = {"cabbage", "sweet-potato"}

BAND = 0.16          # 뭉갠 자국을 찾을 띠 (카드 위쪽)
BLOCK = 8            # 모자이크 블록 크기
FLAT_STD = 1.2       # 블록 안 색이 이만큼도 안 흔들리면 뭉갠 자리
MIN_CELLS = 6        # 이보다 작은 덩어리는 무시
PAD = 6              # 찾은 자리를 사방으로 이만큼 넓혀 메운다
TAIL = 0.15          # 오른쪽 끝 이만큼만 원래 색으로 되돌린다

# 이름칸의 최소 높이(카드 대비 %). 상추처럼 낮게 잡히면 글자가 그 높이에 맞춰
# 작아져서 혼자만 깨알같이 찍힌다. 메우는 자리는 그대로 두고 **글자 칸만** 넓힌다.
MIN_SLOT_H = 5.6

# 바 색을 고를 때 왼쪽 몇 칸을 볼지. **한 칸만 보면 안 된다** — 야채 글자의 흰
# 번짐이 걸리면 메운 자리가 하얘져서 그 위의 흰 글씨가 묻힌다. 여러 칸 중
# 가장 어두운 것을 고른다.
LOOK = (3, 7, 12, 18, 26)

# 이름칸이 있을 수 있는 가로 범위. 야채 글자보다 오른쪽, 카드 끝보다 왼쪽이다.
X_MIN, X_MAX = 0.45, 0.80

# 이 밝기보다 어두우면 제목 바 위로 본다.
BAR_LUM = 105


def fragments(im: Image.Image) -> list[tuple[int, int, int, int]]:
    """뭉갠 것으로 보이는 블록 덩어리들."""
    height = int(im.height * BAND)
    arr = np.asarray(im.convert("RGBA"), dtype=np.int16)[:height]
    h, w, _ = arr.shape
    rows, cols = h // BLOCK, w // BLOCK
    arr = arr[: rows * BLOCK, : cols * BLOCK]
    rgb = arr[..., :3].reshape(rows, BLOCK, cols, BLOCK, 3)
    alpha = arr[..., 3].reshape(rows, BLOCK, cols, BLOCK)
    flat = (
        (rgb.std(axis=(1, 3)).max(axis=2) < FLAT_STD)
        & (alpha.min(axis=(1, 3)) > 200)
        # 카드 밖 검은 여백도 평평하다. 밝기로 뺀다.
        & (rgb.mean(axis=(1, 3)).max(axis=2) > 20)
    )

    seen = np.zeros_like(flat)
    out = []
    for y in range(rows):
        for x in range(cols):
            if not flat[y, x] or seen[y, x]:
                continue
            queue = deque([(y, x)])
            seen[y, x] = True
            cells = []
            while queue:
                cy, cx = queue.popleft()
                cells.append((cy, cx))
                for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                    if 0 <= ny < rows and 0 <= nx < cols and flat[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        queue.append((ny, nx))
            if len(cells) < MIN_CELLS:
                continue
            ys = [c[0] for c in cells]
            xs = [c[1] for c in cells]
            out.append(
                (min(xs) * BLOCK, min(ys) * BLOCK, (max(xs) + 1) * BLOCK, (max(ys) + 1) * BLOCK)
            )
    return out


def on_bar(im: Image.Image, box: tuple[int, int, int, int]) -> bool:
    """조각 바로 왼쪽이 어두우면 제목 바 위다 — 번호판 자국을 여기서 가른다."""
    px = im.load()
    _, h = im.size
    x = max(0, box[0] - 8)
    ys = ((box[1] + box[3]) // 2, box[1] + 2, box[3] - 2)
    return min(sum(px[x, min(h - 1, max(0, y))][:3]) / 3 for y in ys) < BAR_LUM


def name_box(im: Image.Image) -> tuple[int, int, int, int] | None:
    w, _ = im.size
    keep = [
        b
        for b in fragments(im)
        if w * X_MIN < b[0] < w * X_MAX and on_bar(im, b)
    ]
    if not keep:
        return None
    return (
        min(b[0] for b in keep),
        min(b[1] for b in keep),
        max(b[2] for b in keep),
        max(b[3] for b in keep),
    )


def smear(im: Image.Image, box: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    """왼쪽 바 색을 오른쪽으로 늘인다. 넓힌 상자를 돌려준다."""
    w, h = im.size
    x0, y0 = max(0, box[0] - PAD), max(0, box[1] - PAD)
    x1, y1 = min(w, box[2] + PAD), min(h, box[3] + PAD)
    px = im.load()
    span = max(1, x1 - x0)
    tail = max(1, int(span * TAIL))

    def darkest(at: int, y: int, sign: int):
        best = None
        for d in LOOK:
            x = min(w - 1, max(0, at + sign * d))
            c = px[x, y]
            if best is None or sum(c[:3]) < sum(best[:3]):
                best = c
        return best

    for y in range(y0, y1):
        left = darkest(x0, y, -1)
        right = darkest(x1, y, +1)
        for x in range(x0, x1):
            over = x - (x1 - tail)
            t = 0.0 if over < 0 else over / tail
            px[x, y] = tuple(int(left[c] + (right[c] - left[c]) * t) for c in range(4))
    return x0, y0, x1, y1


def main() -> None:
    apply = "--apply" in sys.argv
    slots: dict[str, list[float]] = {}
    for path in sorted(ART.glob("*-card-slots.webp")):
        vid = path.name.replace("-card-slots.webp", "")
        if vid in SKIP:
            print(f"{vid:14s} 이미 제대로 비워져 있다 — 건너뜀")
            continue
        im = Image.open(path).convert("RGBA")
        found = name_box(im)
        if found is None:
            print(f"{vid:14s} ⚠ 이름칸을 못 찾았다")
            continue
        x0, y0, x1, y1 = smear(im, found)
        w, h = im.size
        # 글자 칸은 메운 자리보다 낮지 않게 한다 (위 MIN_SLOT_H 주석).
        need = h * MIN_SLOT_H / 100
        if y1 - y0 < need:
            mid = (y0 + y1) / 2
            y0, y1 = int(mid - need / 2), int(mid + need / 2)
        slots[vid] = [
            round(x0 / w * 100, 2),
            round(max(0, y0) / h * 100, 2),
            round(x1 / w * 100, 2),
            round(min(h, y1) / h * 100, 2),
        ]
        print(f"{vid:14s} name = Slot({', '.join(f'{v}f' for v in slots[vid])})")
        if apply:
            im.save(path, "WEBP", quality=92, method=6)

    print()
    print(json.dumps(slots, indent=1))
    if not apply:
        print("\n--apply 를 안 줘서 파일은 그대로다.")


if __name__ == "__main__":
    main()
