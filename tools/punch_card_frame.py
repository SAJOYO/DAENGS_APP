#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10", "numpy>=1.26", "scipy>=1.11"]
# ///
"""이머시브 **창틀**에서 저쪽 강아지 이름과 번호를 지운다.

    uv run tools/punch_card_frame.py <원본폴더>            # 미리 보기
    uv run tools/punch_card_frame.py <원본폴더> --apply    # 지운다

## 왜 창틀까지 건드리나

진입 연출은 카드가 **녹으면서** 그 아래의 창틀이 드러나는 그림이다. 카드는 우리 것으로
바꿨는데(`drawPersonalCardAt`) 창틀에 저쪽 글자가 박혀 있으면, 녹는 1초 남짓 동안
`SWEET POTATO NEO` 와 `NEO-0824` 가 비친다.

## 지우는 규칙은 카드와 한 벌이다

예전 판은 이름 자리를 **직사각형으로 잡아 좌우 이웃 색으로 메웠다.** 그래서
`SWEET POTATO` 는 남고 뒷말 `NEO` 만 지워졌고, 번호 자리에는 무지개 위로 가로 줄무늬가
남았다. 지금은 카드와 **같은 함수**(`card_text_slots`)를 불러 글자 화소만 마스크로
지운다 — 두 벌이 되면 카드와 창틀에서 글자 자리가 갈린다.

## 번호 자리는 여기서도 못 지운다

창틀 셋(배추·고구마·상추)은 전부 버섯형이라 번호가 홀로그램 위에 있다. 앱이
`ImmersiveScene.frameChip` 으로 어두운 칩을 깔아 덮는다 — 예전 줄무늬도 같이 덮인다.

## 원본에서 시작한다

이미 나가 있는 창틀은 **줄무늬가 구워진 판**이다. 그 위에 또 지우면 줄무늬가 남는다.
저쪽에서 받은 원본(또는 `git show <지우기 전 커밋>:...`)을 폴더에 넣고 돌린다.
"""

from __future__ import annotations

import pathlib
import sys

import numpy as np
from PIL import Image

from card_text_slots import DARK, GLYPH_OVER, GLYPH_WEAK, MIN_AREA, RAIL_MAX_H
from card_text_slots import bar_spans, erase, runs_of
from scipy import ndimage

# 낱자 하나가 이보다 넓으면 글자가 아니다 (창틀 제목은 떨어진 세리프다).
FRAME_GLYPH_MAX_W = 120

ART = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/neo-hologram/art"


def title_box(lum: np.ndarray) -> tuple[int, int, int, int] | None:
    """제목 바 사각형. 카드와 같은 방법이다 — 글자 줄을 기준점으로 삼는다."""
    h, w = lum.shape
    px0, px1 = int(w * 0.28), int(w * 0.64)
    ty0, ty1 = int(h * 0.03), int(h * 0.125)

    lit = (lum[ty0:ty1, px0:px1] > 150).mean(axis=1) > 0.20
    letters = [r for r in runs_of(lit) if r[1] - r[0] > 12]
    if not letters:
        return None
    ls, le = max(letters, key=lambda r: r[1] - r[0])
    ls += ty0
    le += ty0

    picks = []
    for dy in (-10, -8, -6, le - ls + 6, le - ls + 8, le - ls + 10):
        y = ls + dy
        if 0 <= y < h:
            hold = [sp for sp in bar_spans(lum[y] < DARK) if sp[0] <= w // 2 <= sp[1]]
            if hold:
                picks.append(hold[0])
    if not picks:
        return None
    x0 = min(sp[0] for sp in picks)
    x1 = int(np.median([sp[1] for sp in picks]))

    mid = (ls + le) // 2
    best = None
    stop = x0 + max(20, int((x1 - x0) * 0.45))
    for probe in range(x0 + 4, min(stop, x1 - 2), 3):
        got = [r for r in runs_of(lum[: int(h * 0.22), probe] < DARK) if r[0] <= mid < r[1]]
        if got and (best is None or got[0][1] - got[0][0] > best[1] - best[0]):
            best = got[0]
    if best is None:
        return None
    y0, y1 = best

    # **아바타 원을 밴드 밖으로 밀어낸다.**
    #
    # 창틀 왼쪽에는 저쪽 강아지 얼굴이 박힌 밝은 원이 있고, 어두운 바가 그 뒤까지
    # 이어져 있어서 왼쪽 끝이 원 안에서 잡힌다. 그러면 원의 밝은 화소가 "글자" 로
    # 걸려 지워지고, 메우면서 그 밝은 색이 바 오른쪽까지 줄무늬로 끌려간다.
    #
    # 바 높이의 대부분이 어두운 **첫 세로줄**부터 시작한다 — 원이 지나는 줄은
    # 위아래가 밝아서 저절로 빠진다.
    need = (y1 - y0) * 0.80
    x = x0
    while x < x1:
        if (lum[y0:y1, x] < DARK).sum() >= need:
            break
        x += 1
    return x, y0, x1, y1


def punch(src: pathlib.Path, apply: bool) -> list[float] | None:
    veggie = src.name.replace("-card-frame.webp", "")
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    rgba = np.asarray(im, dtype=np.int16).copy()
    rgb = rgba[..., :3]
    lum = rgb.mean(axis=2)

    box = title_box(lum)
    if box is None:
        print(f"{veggie:14s} ⚠ 제목 바를 못 찾았다")
        return None
    x0, y0, x1, y1 = box

    band = lum[y0:y1, x0:x1]
    bg = np.median(band[band < DARK]) if (band < DARK).any() else 0.0
    lab, n = ndimage.label(band > bg + GLYPH_WEAK, np.ones((3, 3)))
    hot = set(np.unique(lab[band > bg + GLYPH_OVER])) - {0}
    keep = np.zeros(band.shape, bool)
    for sl, i in zip(ndimage.find_objects(lab), range(1, n + 1)):
        if i not in hot:
            continue
        if sl[0].stop - sl[0].start < max(RAIL_MAX_H, (y1 - y0) * 0.18):
            continue
        # **폭이 넓은 덩이는 글자가 아니다.** 창틀은 왼쪽에 저쪽 강아지 얼굴이 박힌
        # 밝은 원이 있는데, 그 테두리가 후광을 타고 제목 글자와 한 덩이가 됐다.
        # 그대로 메우면 원의 밝은 색이 바 오른쪽까지 줄무늬로 끌려간다 — 실제로
        # 그렇게 됐다. 카드와 달리 창틀 글자는 낱자가 떨어진 세리프라 폭으로 갈린다.
        if sl[1].stop - sl[1].start > FRAME_GLYPH_MAX_W:
            continue
        if (lab[sl] == i).sum() < MIN_AREA:
            continue
        keep |= lab == i
    if not keep.any():
        print(f"{veggie:14s} ⚠ 글자를 못 찾았다")
        return None

    mask = np.zeros((h, w), bool)
    mask[y0:y1, x0:x1] = keep
    erase(rgb, mask)
    rgba[..., :3] = rgb

    def pct(v, total):
        return round(v / total * 100, 2)

    name = [pct(x0, w), pct(y0, h), pct(x1, w), pct(y1, h)]
    gap, inset = w * 0.016, (y1 - y0) * 0.10
    code = [pct(x1 + gap, w), pct(y0 + inset, h), 94.5, pct(y1 - inset, h)]
    print(f"{veggie:14s} frameName = Slot({', '.join(f'{v}f' for v in name)})")
    print(f"{'':14s} frameCode = Slot({', '.join(f'{v}f' for v in code)})")
    if apply:
        Image.fromarray(rgba.clip(0, 255).astype(np.uint8), "RGBA").save(
            ART / src.name, "WEBP", quality=92, method=6
        )
    return name


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    apply = "--apply" in sys.argv
    if not args:
        raise SystemExit("원본 창틀이 든 폴더를 달라 (지우기 전 판이어야 한다)")
    src = pathlib.Path(args[0])
    found = sorted(src.glob("*-card-frame.webp"))
    if not found:
        raise SystemExit(f"창틀이 없다: {src}")
    for p in found:
        punch(p, apply)
    if not apply:
        print("\n--apply 를 안 줘서 파일은 그대로다.")


if __name__ == "__main__":
    main()
