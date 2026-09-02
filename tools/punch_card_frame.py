#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10"]
# ///
"""이머시브 **창틀**에서 저쪽 강아지 이름과 번호를 지운다.

    uv run tools/punch_card_frame.py            # 미리 보기
    uv run tools/punch_card_frame.py --apply    # 지운다

## 왜 창틀까지 건드리나

진입 연출은 카드가 **녹으면서** 그 아래의 창틀이 드러나는 그림이다. 카드는 우리 것으로
바꿨는데(`drawPersonalCardAt`) 창틀에는 `SWEET POTATO NEO` 와 `NEO-0824` 가 그대로
박혀 있어서, 녹는 1초 남짓 동안 저쪽 글자가 비쳤다.

## 자리는 손으로 쟀다

창틀은 카드와 **크기도 비율도 다른 별도 렌더**다 (고구마 창틀 1067x1474 vs 카드
816x1125). 그래서 `CardSlots.kt` 의 좌표를 그대로 못 쓴다.

자동으로 찾아보려 했는데 은색 프레임의 가로 레일이 글자만큼 밝아서 낱말이 뭉치거나
쪼개졌다 — 상추만 제대로 잡혔다. 눈금을 얹어 놓고 읽는 편이 빨랐다. 셋뿐이다.

## 지우는 방법

`name_slot.py` 와 같다 — 줄마다 왼쪽 이웃 색을 오른쪽으로 늘이고 끝만 되돌린다.
**왼쪽 여러 칸 중 가장 어두운 것**을 고른다. 한 칸만 보면 야채 글자의 흰 번짐이
걸려서 지운 자리가 하얘지고, 그 위에 얹을 흰 글씨가 묻힌다.
"""

from __future__ import annotations

import pathlib
import sys

from PIL import Image

ART = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/neo-hologram/art"

# 창틀 크기 대비 %. (x0, y0, x1, y1)
#   name — 저쪽 강아지 이름(`NEO`)이 있던 자리. 우리 아이 이름이 여기 들어간다
#   code — 번호판. 아이 생일에서 만든 번호가 여기 들어간다
SLOTS = {
    "cabbage": {"name": (51.5, 5.2, 65.0, 9.8), "code": (75.0, 5.2, 94.5, 9.8)},
    "sweet-potato": {"name": (58.8, 5.2, 70.0, 9.8), "code": (73.5, 5.2, 93.0, 9.8)},
    "lettuce": {"name": (53.8, 5.4, 67.0, 9.4), "code": (75.5, 5.2, 95.0, 9.4)},
}

PAD = 4          # 찾은 자리를 사방으로 이만큼 넓혀 지운다
TAIL = 0.15      # 오른쪽 끝 이만큼만 원래 색으로 되돌린다
LOOK = (3, 7, 12, 18, 26)   # 왼쪽 이 칸들 중 가장 어두운 색을 쓴다


def smear(im: Image.Image, box: tuple[float, float, float, float]) -> None:
    w, h = im.size
    x0 = max(0, int(w * box[0] / 100) - PAD)
    y0 = max(0, int(h * box[1] / 100) - PAD)
    x1 = min(w, int(w * box[2] / 100) + PAD)
    y1 = min(h, int(h * box[3] / 100) + PAD)
    px = im.load()
    span = max(1, x1 - x0)
    tail = max(1, int(span * TAIL))

    def darkest(at: int, y: int, sign: int):
        best = None
        for d in LOOK:
            c = px[min(w - 1, max(0, at + sign * d)), y]
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


def main() -> None:
    apply = "--apply" in sys.argv
    for vid, slots in SLOTS.items():
        path = ART / f"{vid}-card-frame.webp"
        if not path.exists():
            print(f"{vid:14s} 창틀이 없다")
            continue
        im = Image.open(path).convert("RGBA")
        for box in slots.values():
            smear(im, box)
        print(
            f"{vid:14s} name={slots['name']}  code={slots['code']}  ({im.width}x{im.height})"
        )
        if apply:
            im.save(path, "WEBP", quality=92, method=6)
    if not apply:
        print("\n--apply 를 안 줘서 파일은 그대로다.")


if __name__ == "__main__":
    main()
