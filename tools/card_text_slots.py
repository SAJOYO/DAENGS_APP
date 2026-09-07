#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10", "numpy>=1.26", "scipy>=1.11"]
# ///
"""저쪽 카드에 **인쇄된 이름과 번호를 지우고**, 우리 글자가 앉을 자리를 잰다.

    uv run tools/card_text_slots.py <드롭폴더>            # 미리 보기 + 검토 시트
    uv run tools/card_text_slots.py <드롭폴더> --apply    # 원화를 고치고 좌표를 찍는다

드롭폴더에는 저쪽이 준 **깨끗한 완성 카드**가 `<야채>-card.png` 로 들어 있다.
얼굴창은 검은 원, 아바타는 흰 원, 이름·번호는 **인쇄된 그대로**다.

## `name_slot.py` 를 대신한다

그건 저쪽이 이름을 **모자이크로 뭉개** 보내던 시절의 도구다. 뭉갠 블록(8px 안에서 색이
안 흔들리는 자리)을 찾아 메웠는데, 자국이 남아서 카드에 지우다 만 티가 났다.
이제는 글자가 인쇄된 채로 오므로 **바탕을 보고 우리가 지운다.**

## 직사각형을 통째로 메우면 안 된다

`punch_card_slots.py` 의 `erase_text` 와 `name_slot.py` 의 `smear` 는 둘 다 글자 자리를
사각형으로 잡아 좌우 이웃 색으로 채운다. 이 카드에서는 그러면 **제목 바의 대각선 잘림과
은색 베벨이 같이 날아간다** — 버섯형 여덟 장은 바 오른쪽이 비스듬히 잘려 있다.

그래서 **글자 화소만 마스크로 잡아** 그 자리만 좌우로 잇는다. 바의 구조는 마스크 밖이라
손대지 않는다.

## 바를 먼저 찾는다

글자는 "어두운 바탕 위의 밝은 화소" 인데, 그 규칙만으로는 카드 아래쪽 기술명·설명글까지
걸린다. 그래서 **제목 바를 먼저 찾고 그 안에서만** 고른다.

바는 **가로로 긴 어두운 구간**이다. 줄마다 어두운 구간을 찾아, 글자 폭(<40px)만큼
떨어진 것끼리 이어 붙이고, 충분히 길고(150px) 충분히 어두운(35%) 것만 바로 본다.
닫기 연산(모폴로지)으로 하면 안 된다 — 홀로그램에 흩어진 어두운 점들이 이어져서
무지개가 통째로 바가 됐다.

## 은색 레일은 글자가 아니다

바 위아래에 밝은 은색 레일이 붙어 있어 밝기만으로는 글자와 안 갈린다. **모양으로
가른다** — 레일은 가로로 길고 얇고(폭 200px+, 높이 <9px), 글자는 뭉치다.

## 번호판은 두 갈래다

바탕 밝기가 가른다. 손으로 목록을 나눠 적지 않는다.

    당근형 4장   번호가 **인쇄된 검은 상자 안**(밝기 0~13)      → 글자를 지운다
    버섯형 8장   번호가 **홀로그램 무지개 위**                   → 못 지운다. 자리만 잰다

무지개는 30px 안에서 (230,219,51) → (234,255,254) → (246,242,255) 로 튄다. 가로로
이으면 노란 띠, 세로로 이으면 줄무늬, 아래를 복제하면 이음매가 보인다. 앱이
`CardTemplate.codeChip` 으로 그 위에 어두운 칩을 깐다.

## 알파는 건드리지 않는다

이미 나가 있는 `<야채>-card-slots.webp` 의 얼굴·아바타 구멍은 실기기에서 검증된
것이다. 드롭폴더의 새 판이 **같은 렌더인지 화소로 확인한 뒤**(평균차 8 미만) 글자띠의
RGB 만 옮겨 심고 알파는 그대로 둔다. 다르면 건너뛰고 알린다 — 구멍을 다시 재는 것은
이 도구의 일이 아니다.
"""

from __future__ import annotations

import json
import pathlib
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ART = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/neo-hologram/art"

# 제목이 있는 띠 (카드 높이 대비). 아래는 기술명이라 넘어가면 안 된다.
TITLE_BAND = (0.015, 0.22)
# 아바타 원 오른쪽부터 본다. 흰 원이 글자로 잡힌다.
TITLE_X = (0.22, 0.99)

DARK = 70          # 이보다 어두우면 바 바탕
GLYPH_OVER = 55    # 바탕보다 이만큼 밝으면 확실한 글자
GLYPH_WEAK = 18    # 씨앗에 붙어 있으면 이 정도만 밝아도 지운다 (후광)
HALO = 6           # 후광을 씨앗에서 이 거리까지만 따라간다 (레일 보호)
GAP = 40           # 이만큼 떨어진 어두운 구간은 한 바로 잇는다 (글자 폭)
MINLEN = 150       # 바로 인정할 최소 길이
DENSITY = 0.35     # 이은 구간이 이만큼은 어두워야 바다

# 레일 거르기 — 레일은 가로로 길고 얇다.
RAIL_MAX_H, RAIL_MAX_W, MIN_AREA = 9, 110, 40
GLYPH_MAX_H = 70

# 마스크를 이만큼 부풀린다. **JPEG 링잉까지 먹어야 한다** — 글자 둘레 편차가
# 먼 바탕의 3~4배(13.0 vs 3.4)라, 좁게 잡으면 지운 자리에 글자 윤곽이 유령으로 남는다.
GROW = 4

# 메울 색을 읽을 때 양 끝에서 볼 화소 수. 한 점만 보면 그 잡음이 줄무늬가 된다.
EDGE = 14

# 새 판이 같은 렌더인지 보는 문턱 (그림 영역 평균 화소차).
SAME_RENDER = 8.0

# 새 판에서 구멍을 잴 때. 얼굴창은 검은 원, 아바타는 흰 원으로 비워져 온다.
HOLE_DARK, HOLE_LIGHT = 34, 244
# 찾은 원을 이만큼 키워 뚫는다. 표시 원 가장자리에 저쪽 안티에일리어싱이 한 겹 남는다.
HOLE_GROW = 1.03


def runs_of(mask: np.ndarray) -> list[tuple[int, int]]:
    d = np.diff(np.concatenate(([0], mask.view(np.int8), [0])))
    return list(zip(np.nonzero(d == 1)[0], np.nonzero(d == -1)[0]))


def bar_spans(row_dark: np.ndarray) -> list[tuple[int, int]]:
    """한 줄에서 **바로 볼 만한** 어두운 구간들."""
    r = runs_of(row_dark)
    if not r:
        return []
    merged = [list(r[0])]
    for s, e in r[1:]:
        if s - merged[-1][1] < GAP:
            merged[-1][1] = e
        else:
            merged.append([s, e])
    return [(s, e) for s, e in merged
            if e - s >= MINLEN and row_dark[s:e].mean() >= DENSITY]


def bar_mask(lum: np.ndarray) -> np.ndarray:
    out = np.zeros(lum.shape, bool)
    for i in range(lum.shape[0]):
        for s, e in bar_spans(lum[i] < DARK):
            out[i, s:e] = True
    return out


def glyph_mask(lum: np.ndarray, bar: np.ndarray) -> np.ndarray:
    """바 안의 글자 화소. 레일은 모양으로 뺀다."""
    bright = np.zeros(lum.shape, bool)
    for i in range(lum.shape[0]):
        span = bar[i]
        if not span.any():
            continue
        bg = np.median(lum[i][span & (lum[i] < DARK)]) if (span & (lum[i] < DARK)).any() else 0.0
        bright[i] = span & (lum[i] > bg + GLYPH_OVER)

    lab, n = ndimage.label(bright, np.ones((3, 3)))
    keep = np.zeros_like(bright)
    for sl, i in zip(ndimage.find_objects(lab), range(1, n + 1)):
        h = sl[0].stop - sl[0].start
        w = sl[1].stop - sl[1].start
        if h < RAIL_MAX_H or h > GLYPH_MAX_H or w > RAIL_MAX_W:
            continue
        if (lab[sl] == i).sum() < MIN_AREA:
            continue
        keep |= lab == i
    return keep


def erase(rgb: np.ndarray, mask: np.ndarray) -> None:
    """마스크 화소만 **같은 줄의 좌우 이웃**으로 잇는다. 제자리에서 고친다.

    **양 끝을 한 화소로 읽으면 안 된다.** 처음엔 `rgb[y, s-1]` 과 `rgb[y, e]` 를 그대로
    썼는데, 그 한 점에 실린 잡음이 줄마다 달라서 **줄마다 다른 기울기의 그라디언트**가
    깔렸다 — 당근형 제목 바에 가로 줄무늬가 남은 것이 이것이다. 끝 여러 화소의
    **가운뎃값**을 쓰면 잡음이 죽고 그라디언트는 그대로 따라간다.
    """
    grown = ndimage.binary_dilation(mask, np.ones((GROW * 2 + 1, GROW * 2 + 1)))
    w = rgb.shape[1]
    for y in np.nonzero(grown.any(axis=1))[0]:
        row = grown[y]
        for a, b in runs_of(row):
            def side(lo: int, hi: int) -> np.ndarray | None:
                lo, hi = max(0, lo), min(w, hi)
                if hi <= lo:
                    return None
                free = ~row[lo:hi]
                if not free.any():
                    return None
                return np.median(rgb[y, lo:hi][free], axis=0)

            left = side(a - EDGE, a)
            right = side(b, b + EDGE)
            if left is None and right is None:
                continue
            if left is None:
                left = right
            if right is None:
                right = left
            n = b - a
            t = (np.arange(n) + 1.0) / (n + 1.0)
            rgb[y, a:b] = (left[None, :] * (1 - t)[:, None]
                           + right[None, :] * t[:, None]).round()


def rows_of(mask: np.ndarray, need: int = 5) -> list[tuple[int, int]]:
    on = mask.sum(axis=1) > need
    out, s = [], None
    for i, f in enumerate(on):
        if f and s is None:
            s = i
        if not f and s is not None:
            if i - s > 6:
                out.append((s, i))
            s = None
    if s is not None and len(on) - s > 6:
        out.append((s, len(on)))
    return out


# -- 번호판 ------------------------------------------------------------------
#
# 버섯형은 번호가 홀로그램 위 **흰 글자**다. 무지개는 색이 진해서(채도 높음) 흰
# 글자가 갈린다 — 밝고 무채색인 화소만 고른다.

CODE_X = (0.68, 0.99)          # 버섯형: 제목 바 오른쪽
CODE_BAND = (0.015, 0.14)
WHITE_MIN, WHITE_SPREAD = 190, 45

# 당근형: 번호가 카드 아래쪽 왼편 검은 상자 안에 있다.
BOX_BAND = (0.74, 0.90)
BOX_X = (0.02, 0.32)


def white_text(img: np.ndarray) -> np.ndarray:
    lo = img.min(axis=2)
    hi = img.max(axis=2)
    return (lo > WHITE_MIN) & (hi - lo < WHITE_SPREAD)


# 당근형 넉 장의 번호 상자. **카드 크기 대비 %** 이고 (x0, y0, x1, y1) 다.
#
# 자동으로 찾다 포기했다. 그 자리 바로 아래에 지구본 아이콘 상자가 하나 더 있는데
# 아이콘이 글자보다 밝은 화소가 많아서 그쪽이 뽑혔고, 정작 `NEO-Y0824` 는 그대로
# 남았다. 모양(가로로 긴 줄)으로 걸러 봐도 게이지 막대에 걸려 흔들렸다.
#
# **눈금을 얹어 놓고 읽었다** — `punch_card_frame.py` 가 창틀 셋에서 쓴 그 방법이고,
# 여기도 넷뿐이다. 값은 인쇄된 검은 상자의 **안쪽**이라 그 안의 글자를 다 덮는다.
# 이름칸의 **왼쪽 끝을 손으로 미는 카드.** 카드 폭 대비 %.
#
# 제목 바가 아바타 원 **뒤까지** 뻗어 있는 판이 있다. 그러면 지우는 범위가 아바타를
# 물고, `erase` 가 그 줄을 좌우 이웃으로 이으면서 **은테와 그림 위로 검은 띠가 번진다.**
# 구멍 안은 투명이라 안 보이지만 테두리는 그대로 보인다.
#
# 과일 `kiwi-gentle`(GENTLE MONSTER)에서 실측했다 — 이름칸이 `x0 = 5.35%` 로 잡혀
# 아바타(8.4~15.0%)를 통째로 물었다. 나머지 열두 장은 18~19% 라 안 물린다.
# **자동으로 아바타를 피하게 만들지 않는다** — 그러면 야채 열두 장의 이미 굳은 값이
# 같이 움직인다 (배추는 19.63% 인데 아바타 오른쪽 끝이 22.33% 다).
NAME_X0_MIN = {
    "kiwi-gentle": 24.0,
}

CODE_BOX = {
    "carrot":   (4.44, 78.75, 25.19, 83.33),
    "pepper":   (4.44, 78.75, 25.19, 83.33),
    "danhobak": (4.44, 79.31, 24.81, 83.89),
    "eggplant": (4.81, 79.93, 25.56, 84.51),
}


def find_code(
    veggie: str, w: int, h: int, nx0: int, nx1: int, ny0: int, ny1: int
) -> tuple[tuple[int, int, int, int] | None, bool]:
    """번호 자리와 **칩을 깔지**를 돌려준다.

    ## 두 디자인은 제목 바 폭으로 갈린다

    버섯형은 오른쪽 위에 번호판이 따로 있어서 제목 바가 거기서 끝난다(카드 폭의
    43~55%). 당근형은 번호가 아래로 내려가 있어 바가 쭉 뻗는다(67~70%). 이 차이는
    구조에서 오는 것이라 흔들리지 않는다 — 밝기나 색으로 가르려던 것은 다 실패했다.

    ## 버섯형은 찾지 않고 계산한다

    번호 글자를 무지개 위에서 찾아보려 했는데, 버섯·오이·시금치·토마토는 흰 글자가
    **거의 흰 바탕** 위에 얹혀 있어서 글자와 바탕이 한 덩이로 잡혔다. 어차피 칩으로
    덮을 자리라 글자를 정확히 딸 이유가 없다 — **제목 바 오른쪽, 바와 같은 높이**면
    된다. 프레임이 공통이라 이 관계는 카드마다 안 변한다.
    """
    if (nx1 - nx0) / w <= 0.60:
        gap = w * 0.016
        inset = (ny1 - ny0) * 0.10
        return (int(nx1 + gap), int(ny0 + inset), int(w * 0.945), int(ny1 - inset)), True

    box = CODE_BOX.get(veggie)
    if box is None:
        return None, False
    return (int(box[0] / 100 * w), int(box[1] / 100 * h),
            int(box[2] / 100 * w), int(box[3] / 100 * h)), False


# -- 한 장 처리 ---------------------------------------------------------------


def punch_markers(rgb: np.ndarray) -> tuple[dict, np.ndarray] | None:
    """표시 원 둘을 재서 알파를 뚫는다.

    새 판은 **얼굴창이 검은 원, 아바타가 흰 원**으로 비어 온다 (`docs/card-holes.md`).
    같은 렌더가 이미 나가 있으면 이 길로 안 온다 — 실기기에서 검증된 구멍을 그대로
    쓰는 편이 낫다. 캔버스가 바뀐 판에서만 쓴다.

    **외접상자로 재면 안 된다.** 가지는 몸통이 거의 검정이라 구멍이 몸통과 한 덩이로
    붙어 상자가 755x670 이 됐다 (저쪽 `measure_holes.py` 가 겪은 것). 거리 변환으로
    **최대 내접원**을 찾으면 붙은 조각에 안 흔들린다.
    """
    h, w, _ = rgb.shape
    lum = rgb.mean(axis=2)
    spread = rgb.max(axis=2) - rgb.min(axis=2)

    def biggest_circle(mask: np.ndarray) -> tuple[float, float, float] | None:
        lab, n = ndimage.label(mask)
        if not n:
            return None
        best = None
        for i in range(1, n + 1):
            blob = lab == i
            # 카드 밖 검은 여백은 테두리에 닿는다. 빼야 얼굴창이 남는다.
            if blob[0].any() or blob[-1].any() or blob[:, 0].any() or blob[:, -1].any():
                continue
            if blob.sum() < 4000:
                continue
            dist = ndimage.distance_transform_edt(blob)
            r = float(dist.max())
            if best is None or r > best[2]:
                cy, cx = np.unravel_index(int(np.argmax(dist)), dist.shape)
                best = (float(cx), float(cy), r)
        return best

    face = biggest_circle(lum < HOLE_DARK)
    avatar = biggest_circle((lum > HOLE_LIGHT) & (spread < 18))
    if face is None or avatar is None:
        return None

    hole = np.zeros((h, w), np.uint8)
    yy, xx = np.mgrid[0:h, 0:w]
    out = {}
    for key, (cx, cy, r) in (("face", face), ("avatar", avatar)):
        rr = r * HOLE_GROW
        hole |= ((xx - cx) ** 2 + (yy - cy) ** 2 <= rr * rr).astype(np.uint8)
        out[key] = (round(cx / w * 100, 2), round(cy / h * 100, 2),
                    round(rr / w * 100, 2), round(rr / h * 100, 2))
    return out, hole.astype(bool)


def process(veggie: str, drop: pathlib.Path) -> dict | None:
    dst = ART / f"{veggie}-card-slots.webp"
    if not dst.exists():
        print(f"{veggie:14s} 나가 있는 판이 없다 — 건너뜀")
        return None
    cur = Image.open(dst).convert("RGBA")
    w, h = cur.size
    rgba = np.asarray(cur, dtype=np.int16).copy()

    src = next((p for p in (drop / f"{veggie}-card.png", drop / f"{veggie}-card.webp",
                            drop / f"{veggie}-card.jpg") if p.exists()), None)
    if src is None:
        # **드롭폴더에 원화가 없으면 건드리지 않는다.**
        #
        # 예전에는 그냥 나가 있는 판으로 진행했는데, 그 판은 **이미 글자가 지워진**
        # 그림이라 바 탐색이 엉뚱한 곳을 문다. 과일 한 벌을 반입하면서 실측했다 —
        # 드롭폴더에 과일만 넣고 돌렸더니 당근·단호박·가지·피망이
        # `Slot(0.0, 2.57, 100.0, 4.72)` 처럼 카드 폭 전체를 이름칸으로 냈다.
        # 그대로 `--apply` 했으면 멀쩡한 야채 판 다섯 장이 다시 지워질 뻔했다.
        #
        # 한 벌만 반입할 때 다른 벌을 안 건드리는 것이 이 줄의 값이다.
        return None
    if src is not None:
        clean = Image.open(src).convert("RGB")
        c = np.asarray(clean, dtype=np.int16)
        if clean.size != cur.size:
            # **캔버스가 바뀐 판이다.** 구멍을 물려받을 수 없으니 새로 뚫는다.
            got = punch_markers(c)
            if got is None:
                print(f"{veggie:14s} ⚠ 표시 원을 못 찾았다 — 건너뜀")
                return None
            holes, hole = got
            w, h = clean.size
            rgba = np.dstack([c, np.full((h, w), 255, np.int16)])
            rgba[..., 3][hole] = 0
            fh, av = holes["face"], holes["avatar"]
            print(f"{veggie:14s} 새 캔버스 {clean.size}, 구멍을 새로 뚫었다")
            print(f"{'':14s}   face   = Hole({fh[0]}f, {fh[1]}f, {fh[2]}f, {fh[3]}f)")
            print(f"{'':14s}   avatar = Hole({av[0]}f, {av[1]}f, {av[2]}f, {av[3]}f)")
        else:
            # 같은 렌더인지 화소로 확인하고, 맞으면 **글자띠 RGB 만** 옮겨 심는다.
            keep = rgba[..., 3] > 250
            keep[: int(h * TITLE_BAND[1])] = False
            diff = np.abs(c - rgba[..., :3]).mean(axis=2)[keep].mean()
            if diff > SAME_RENDER:
                print(f"{veggie:14s} ⚠ 다른 렌더다 (평균차 {diff:.1f}) — 구멍을 다시 "
                      f"재야 한다. 건너뜀")
                return None
            # **그림 전체를 갈아 끼운다.** 글자띠만 옮기면 두 번째 돌릴 때 아래쪽은
            # 이미 지워진 상태로 들어와서, 번호판 탐색이 그 옆의 **다른 글자**를 새로
            # 문다 (당근형에서 번호 좌표가 4% 밀렸다). 매번 인쇄된 원본에서 시작해야
            # 몇 번을 돌려도 같은 결과가 나온다.
            rgba[..., :3] = c

    rgb = rgba[..., :3]
    lum = rgb.mean(axis=2)

    # -- 제목 바 --------------------------------------------------------
    #
    # **글자 줄을 기준점으로 삼는다.** 바를 연결요소나 마스크로 잡으려던 시도는 다
    # 실패했다 — 홀로그램 쪽으로 어두운 점이 이어져 칸이 카드 폭 90% 까지 늘어나고,
    # 당근형은 아래 `VEGGIE DOG` 띠와 한 덩이가 됐다. 반면 **글자 줄은 열두 장 모두
    # 한 번에 맞는다**: 고정 창에서 밝은 화소 비율이 높은 줄이 곧 제목이다.
    #
    # 거기서 두 번만 더 재면 된다.
    #   가로 — 글자 **바로 위**의 배경 줄에서 어두운 구간
    #   세로 — 그 구간 왼쪽 안쪽의 세로줄에서 어두운 구간
    # 둘 다 글자를 안 지나므로 흔들릴 것이 없다.
    px0, px1 = int(w * 0.28), int(w * 0.64)
    # 창을 `VEGGIE DOG` 띠 위에서 끊는다. 넓게 두면 그 띠까지 한 줄뭉치가 되어
    # 제목보다 긴 덩이가 이기고, 그 뒤 계산이 통째로 어긋난다.
    ty0, ty1 = int(h * 0.03), int(h * 0.125)

    win = lum[ty0:ty1, px0:px1]
    lit = (win > 150).mean(axis=1) > 0.20
    letters = [r for r in runs_of(lit) if r[1] - r[0] > 12]
    if not letters:
        print(f"{veggie:14s} ⚠ 제목 글자 줄을 못 찾았다")
        return None
    ls, le = max(letters, key=lambda r: r[1] - r[0])
    ls += ty0
    le += ty0

    # 가로는 글자 위아래의 **여러 배경 줄**에서 재고 모은다. 한 줄만 보면 그 줄에
    # 걸린 반짝임 하나에 바 왼쪽 끝이 밀린다 — 토마토에서 `T` 가 칸 밖으로 밀려나
    # 혼자 안 지워졌다.
    picks = []
    for dy in (-10, -8, -6, le - ls + 6, le - ls + 8, le - ls + 10):
        y = ls + dy
        if not (0 <= y < h):
            continue
        hold = [sp for sp in bar_spans(lum[y] < DARK) if sp[0] <= w // 2 <= sp[1]]
        if hold:
            picks.append(hold[0])
    if not picks:
        print(f"{veggie:14s} ⚠ 글자 둘레에서 바를 못 잡았다")
        return None
    # 왼쪽은 제일 바깥, 오른쪽은 가운뎃값. 오른쪽은 대각선으로 잘려서 줄마다 다르다.
    nx0 = min(sp[0] for sp in picks)
    nx1 = int(np.median([sp[1] for sp in picks]))
    floor = NAME_X0_MIN.get(veggie)
    if floor is not None:
        nx0 = max(nx0, int(w * floor / 100))

    # 세로는 **바 왼쪽 안쪽의 여러 세로줄 중 가장 긴 것**으로 잰다. 한 줄만 보면
    # 그 자리에 글자 획이나 베벨이 걸렸을 때 바가 토막 난다.
    # 세로는 바 **왼쪽 절반의 세로줄을 다 훑어 가장 긴 어둠**을 고른다.
    #
    # 한 자리에서 재면 안 된다 — 당근형은 바가 아바타 원 뒤까지 뻗어 있어서 왼쪽 끝
    # 탐침이 **흰 원**을 짚었고, 바 높이가 13px 로 나와 제목이 통째로 안 지워졌다.
    # 원에 걸린 줄은 짧게 나오므로 제일 긴 것을 고르면 저절로 걸러진다.
    mid = (ls + le) // 2
    best = None
    stop = nx0 + max(20, int((nx1 - nx0) * 0.45))
    for probe in range(nx0 + 4, min(stop, nx1 - 2), 3):
        col = lum[: int(h * 0.22), probe] < DARK
        got = [r for r in runs_of(col) if r[0] <= mid < r[1]]
        if got and (best is None or got[0][1] - got[0][0] > best[1] - best[0]):
            best = got[0]
    if best is None:
        print(f"{veggie:14s} ⚠ 세로로 바를 못 잡았다 (x={nx0 + 4}~{stop})")
        return None
    ny0, ny1 = best

    # **왼쪽 끝이 밀리는 곳에서 자른다.** 당근형은 제목 상자 바로 아래에 `VEGGIE DOG`
    # 상자가 붙어 있고 둘 다 순검정이라 세로로는 한 덩이로 읽힌다.
    #
    # 폭으로 자르려다 실패했다 — 버섯형 바는 오른쪽이 비스듬히 잘려서 아래로 갈수록
    # 저절로 좁아지고, 그 바닥이 통째로 잘려 나갔다. **대각선은 오른쪽만 깎는다.**
    # `VEGGIE DOG` 상자는 가운데 정렬이라 **왼쪽 끝이 확 밀린다** — 그것으로 가른다.
    def left_at(y: int) -> int | None:
        hold = [sp for sp in bar_spans(lum[y] < DARK) if sp[0] <= w // 2 <= sp[1]]
        return hold[0][0] if hold else None

    anchor = left_at(mid)
    if anchor is not None:
        jump = int(w * 0.04)
        kept = (ny0, ny1)
        while ny0 + 1 < mid:
            got = left_at(ny0)
            if got is not None and abs(got - anchor) <= jump:
                break
            ny0 += 1
        while ny1 - 1 > mid:
            got = left_at(ny1 - 1)
            if got is not None and abs(got - anchor) <= jump:
                break
            ny1 -= 1
        # **글자보다 얇아졌으면 앵커를 믿지 않는다.**
        #
        # `left_at` 은 "바 폭의 절반을 넘는 어두운 구간" 의 왼쪽 끝을 앵커로 삼는데,
        # 글자 사이가 넓은 카드에서는 글자가 그 줄을 토막 내서 **왼쪽 조각이 절반을
        # 못 넘긴다.** 그러면 더 오른쪽 조각이 앵커가 되고, 그 값과 안 맞는 줄을
        # 전부 깎아 내면서 바가 통째로 사라진다.
        #
        # 과일 복숭아에서 실측했다 — 앵커가 522(멜론은 225)로 잡혀 바가 98px 에서
        # **7px** 로 줄었고, 그 안에서 글자를 못 찾아 카드가 통째로 건너뛰어졌다.
        # 같은 캔버스(1048×1498)의 멜론은 멀쩡했다.
        #
        # 잘라 낸 결과가 글자 띠보다 얇으면 자르기 전으로 되돌린다. 제대로 잘린
        # 카드는 언제나 글자보다 두꺼우므로(멜론 92 > 51) 이 줄에 안 걸린다.
        if ny1 - ny0 < le - ls:
            ny0, ny1 = kept

    # **검은 판이 통째로 잡히면 글자 높이로 되돌린다.**
    #
    # 당근형은 위쪽이 하나의 큰 검은 판이고 제목과 `VEGGIE DOG` 가 그 안에 같이 있다 —
    # 자를 경계가 아예 없다. 버섯형은 바 높이가 글자의 2.2배쯤인데 당근형은 2.8~3.4배라
    # 이 비율로 갈린다. 그 경우 이름칸은 **글자 띠에 여백을 준 것**이 옳다.
    # **지우는 범위와 이름칸은 다르다.** 지우는 것은 검은 판 전체에서 하고(글자가
    # 판 위쪽까지 뻗어 있다), 이름칸만 좁힌다. 한 값으로 묶었더니 당근형에서 글자
    # 윗동이 칸 밖으로 나가 안 지워지고 유령처럼 남았다.
    ey0, ey1 = ny0, ny1
    lh = le - ls
    if lh > 0 and (ny1 - ny0) > lh * 2.6:
        pad = int(lh * 0.35)
        ny0, ny1 = max(ny0, ls - pad), min(ny1, le + pad)

    # 지울 글자 = **그 사각형 안**의 밝은 화소. 밖은 손대지 않는다.
    band = lum[ey0:ey1, nx0:nx1]
    bg = np.median(band[band < DARK]) if (band < DARK).any() else 0.0

    # **문턱 하나로는 후광이 남는다.** 당근형 글자는 굵은 외곽선에 빛번짐이 있어서,
    # 확실한 글자만 지우면 그 둘레에 회색 얼룩이 띠처럼 남았다. 확실한 화소를
    # 씨앗으로 삼아 **약한 문턱까지 번져 나간다** (히스테리시스).
    strong = band > bg + GLYPH_OVER
    # **번지는 거리를 묶는다.** 연결요소를 통째로 가져오면 글자의 빛번짐이 바 위쪽
    # 은색 레일에 닿아 있어서 레일까지 한 덩이가 되고, 지우면 레일이 끊긴다.
    weak = (band > bg + GLYPH_WEAK) & ndimage.binary_dilation(
        strong, np.ones((3, 3)), iterations=HALO
    )
    lab, n = ndimage.label(weak, np.ones((3, 3)))
    hot = set(np.unique(lab[strong])) - {0}
    keep = np.zeros(band.shape, bool)
    for sl, i in zip(ndimage.find_objects(lab), range(1, n + 1)):
        if i not in hot:
            continue
        gh = sl[0].stop - sl[0].start
        # **폭으로 거르면 안 된다.** 당근형은 글자에 굵은 외곽선이 있어 낱말이 통째로
        # 한 덩이가 되고 폭이 450px 까지 간다 — 폭 제한을 두면 제목이 안 지워진다.
        # 레일은 **바 높이에 비해 얇다**는 것으로 갈린다.
        if gh < max(RAIL_MAX_H, (ey1 - ey0) * 0.18):
            continue
        if (lab[sl] == i).sum() < MIN_AREA:
            continue
        keep |= lab == i
    if not keep.any():
        print(f"{veggie:14s} ⚠ 제목 글자를 못 찾았다")
        return None
    full = np.zeros((h, w), bool)
    full[ey0:ey1, nx0:nx1] = keep

    # 제목 글자를 지운다.
    erase(rgb, full)

    code, chip = find_code(veggie, w, h, nx0, nx1, ny0, ny1)
    if code is not None:
        cx0, cy0, cx1, cy1 = code
        if not chip:
            m = np.zeros((h, w), bool)
            m[cy0:cy1, cx0:cx1] = True
            box = lum[cy0:cy1, cx0:cx1]
            bg = np.median(box[box < DARK]) if (box < DARK).any() else 0.0
            m[cy0:cy1, cx0:cx1] &= lum[cy0:cy1, cx0:cx1] > bg + GLYPH_OVER
            erase(rgb, m)

    rgba[..., :3] = rgb
    out = Image.fromarray(rgba.clip(0, 255).astype(np.uint8), "RGBA")

    def pct(x0, y0, x1, y1):
        return [round(x0 / w * 100, 2), round(y0 / h * 100, 2),
                round(x1 / w * 100, 2), round(y1 / h * 100, 2)]

    return {
        "image": out,
        "name": pct(nx0, ny0, nx1, ny1),
        "code": pct(*code) if code else None,
        "chip": chip,
    }


# -- 검토 시트 ---------------------------------------------------------------
#
# **열두 장을 한 장에 붙여서 눈으로 본다.** `punch_card_frame.py` 가 "자동으로 찾아
# 보려 했는데 은색 레일이 글자만큼 밝아서 낱말이 뭉치거나 쪼개졌다 — 눈금을 얹어 놓고
# 읽는 편이 빨랐다" 고 적어 둔 그 방식이다. 숫자만 찍어 놓으면 어긋난 것을 못 본다.

def sheet(items: list[tuple[str, Image.Image, dict]], path: pathlib.Path) -> None:
    tiles = []
    for veggie, img, slot in items:
        w, h = img.size
        flat = Image.new("RGB", img.size, (18, 18, 22))
        flat.paste(img, (0, 0), img)
        d = ImageDraw.Draw(flat)
        x0, y0, x1, y1 = slot["name"]
        d.rectangle([x0 / 100 * w, y0 / 100 * h, x1 / 100 * w, y1 / 100 * h],
                    outline=(255, 70, 70), width=4)
        if slot["code"]:
            x0, y0, x1, y1 = slot["code"]
            colour = (255, 210, 60) if slot["chip"] else (70, 220, 255)
            d.rectangle([x0 / 100 * w, y0 / 100 * h, x1 / 100 * w, y1 / 100 * h],
                        outline=colour, width=4)
        tiles.append((veggie, flat))

    cols, tw = 3, 360
    th = int(tw * tiles[0][1].size[1] / tiles[0][1].size[0])
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new("RGB", (cols * tw + 20, rows * (th + 22) + 10), (12, 12, 16))
    dd = ImageDraw.Draw(out)
    for i, (veggie, img) in enumerate(tiles):
        cx = (i % cols) * tw + 10
        cy = (i // cols) * (th + 22) + 8
        dd.text((cx + 2, cy), veggie, fill=(170, 200, 230))
        out.paste(img.resize((tw - 8, th)), (cx, cy + 14))
    out.save(path)
    print(f"\n검토 시트 → {path}")
    print("  빨강 = 이름칸 · 노랑 = 칩을 깔 자리 · 하늘 = 지운 번호 자리")


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    apply = "--apply" in sys.argv
    if not args:
        print(__doc__.strip().splitlines()[2].strip())
        raise SystemExit(2)
    drop = pathlib.Path(args[0])
    if not drop.is_dir():
        raise SystemExit(f"드롭폴더가 없다: {drop}")

    veggies = sorted({p.name.split("-card-slots.webp")[0]
                      for p in ART.glob("*-card-slots.webp")})
    done, items = {}, []
    for veggie in veggies:
        got = process(veggie, drop)
        if not got:
            continue
        done[veggie] = {"name": got["name"], "code": got["code"], "chip": got["chip"]}
        items.append((veggie, got["image"], got))
        n = got["name"]
        c = got["code"]
        tag = "칩" if got["chip"] else ("지움" if c else "없음")
        print(f"{veggie:14s} name = Slot({n[0]}f, {n[1]}f, {n[2]}f, {n[3]}f)"
              f"   번호 {tag}"
              + (f" Slot({c[0]}f, {c[1]}f, {c[2]}f, {c[3]}f)" if c else ""))
        if apply:
            got["image"].save(ART / f"{veggie}-card-slots.webp", "WEBP",
                              quality=92, method=6)

    if items:
        # 검토 시트는 **저장소 밖**, 드롭폴더 옆에 쓴다. 결과물이라 커밋할 것이 아니다.
        sheet(items, drop / "card-slots-review.png")
    print()
    print(json.dumps(done, ensure_ascii=False, indent=1))
    if not apply:
        print("\n--apply 를 안 줘서 원화는 그대로다.")


if __name__ == "__main__":
    main()
