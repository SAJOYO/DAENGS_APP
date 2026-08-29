#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10"]
# ///
"""완성 카드에서 **개인화할 자리 넷**을 비운다.

저쪽(`SAJOYO/DAENGS_CARDS`)이 주는 `*-card.webp` 는 프레임·이름·번호·얼굴이 전부
인쇄된 완성 카드다. 우리 개의 카드로 만들려면 네 자리를 비워야 한다.

    아바타 원   왼쪽 위 작은 얼굴      → 뚫는다(알파)
    큰 얼굴     그림 한가운데          → 뚫는다(알파)
    이름        "CABBAGE NEO"          → 지운다(바 색으로 메움)
    번호        "NEO-0824"             → 지운다

## 뚫는 것과 지우는 것이 다르다

**얼굴은 뚫는다.** 알파를 깎아 구멍을 내고, 앱이 그 뒤에 우리 개 얼굴을 깐다.
구멍보다 얼굴을 조금 크게 그리면 구멍 테두리가 얼굴 가장자리를 물어서 끼어 든
것으로 보이고, **누끼에서 제일 어려운 털 가장자리가 그 밑으로 숨는다.**

**글자는 못 뚫는다.** 뚫으면 바의 홀로그램 무늬까지 사라져 검은 구멍이 남는다.
대신 글자 자리를 **좌우 이웃 색으로 메운다** — 바가 가로 그라디언트라 줄마다 왼쪽
끝과 오른쪽 끝을 이어 주면 빈 바처럼 보인다. 그 위에 앱이 글자를 그린다.

## 얼굴 자리는 찾고, 글자 자리는 정해 둔다

얼굴은 **색조로 찾는다.** 강아지 얼굴만 따뜻한 크림색(색조 15~50°)이고, 배추는 초록,
고구마는 보라, 홀로그램 배경은 무채색이라 전부 빠진다. 채도만 보고 "낮으면 얼굴" 로
잡았더니 흰빛 배경이 같이 걸려 덩어리 하나가 카드 전체가 됐다.

색조만으로도 부족해서 **생김새**로 한 번 더 거른다 (반지름과 가로세로 비) — 넓이만
보면 배경 덩어리가 늘 이긴다.

글자는 못 찾는다 — 대신 **프레임 배치가 12장 공통**이라 한 벌만 재 두면 된다.

    uv run tools/punch_card_slots.py
"""

from __future__ import annotations

import colorsys
import json
import pathlib
from collections import deque

from PIL import Image, ImageDraw, ImageFilter

ART = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/neo-hologram/art"

CARDS = ("cabbage", "sweet-potato")

# 얼굴 판정 — **채도만 보면 안 된다.**
#
# 처음에는 "채도가 낮으면 얼굴" 로 잡았는데, 홀로그램 배경이 흰빛이라 같이 걸려서
# 덩어리 하나가 카드 전체(rx 45%)가 됐다. 강아지 얼굴은 **따뜻한 크림색**이라
# 색조까지 봐야 갈린다.
#
#   강아지 크림  (230,200,165)  색조 32°  채도 .28  밝기 .90   ← 이것만 남긴다
#   홀로 배경    (240,245,250)  색조  —   채도 .04            무채색이라 빠짐
#   배추 초록                   색조 90°                      색조로 빠짐
#   고구마 보라                 색조 340°                     색조로 빠짐
HUE_MIN, HUE_MAX = 15.0, 50.0      # 도(度)
SATURATION_MIN, SATURATION_MAX = 0.12, 0.46
VALUE_MIN = 0.50

# 덩어리가 이 화소 수보다 작으면 무시한다. 하이라이트 반점을 거른다.
MIN_BLOB = 2000

# 얼굴 덩어리의 생김새 제한. 카드 폭 대비 반지름과 가로/세로 비다.
# **이게 없으면 배경 덩어리가 큰 얼굴로 뽑힌다** — 넓이만 보면 그쪽이 늘 크다.
FACE_R_MIN, FACE_R_MAX = 0.06, 0.28
FACE_ASPECT_MIN, FACE_ASPECT_MAX = 0.55, 1.8

# 찾은 타원을 이만큼 키워 뚫는다.
#
# **줄이면 안 된다.** 처음에 0.92 로 줄였더니 구멍 둘레에 저쪽 강아지 털이 고리처럼
# 남았다 — 탐지가 잡는 것은 **잎에 안 가린 얼굴**이라 실제 구멍(잎이 시작되는 자리)
# 보다 작기 때문이다. 조금 키워서 그 털까지 먹는다. 너무 키우면 잎을 베어 문다.
SHRINK = 1.07

# 구멍 가장자리를 흐리는 폭(짧은 반지름 대비).
FEATHER = 0.05

# 글자 자리. 카드 크기 대비 % 이고 (x0, y0, x1, y1) 다. 프레임 배치가 공통이라
# 12장이 같은 값을 쓴다. cabbage-card.webp(810x1125)에서 쟀다.
TEXT_SLOTS = {
    "name": (24.0, 4.0, 70.0, 9.8),
    "code": (75.0, 4.8, 94.0, 9.4),
}


def blobs(img: Image.Image) -> list[tuple[int, int, int, int, int]]:
    """저채도 덩어리를 전부 찾는다. (넓이, left, top, right, bottom) 을 큰 것부터."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    alpha = img.getchannel("A").load()

    ok = bytearray(w * h)
    for y in range(h):
        for x in range(w):
            if alpha[x, y] < 8:
                continue
            r, g, b = px[x, y]
            hue, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if not (SATURATION_MIN <= s <= SATURATION_MAX and v >= VALUE_MIN):
                continue
            if HUE_MIN <= hue * 360 <= HUE_MAX:
                ok[y * w + x] = 1

    seen = bytearray(w * h)
    out = []
    for sy in range(h):
        for sx in range(w):
            i = sy * w + sx
            if not ok[i] or seen[i]:
                continue
            q = deque([(sx, sy)])
            seen[i] = 1
            n = 0
            left, top, right, bottom = w, h, -1, -1
            while q:
                x, y = q.popleft()
                n += 1
                if x < left:
                    left = x
                if x > right:
                    right = x
                if y < top:
                    top = y
                if y > bottom:
                    bottom = y
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    j = ny * w + nx
                    if 0 <= nx < w and 0 <= ny < h and ok[j] and not seen[j]:
                        seen[j] = 1
                        q.append((nx, ny))
            if n >= MIN_BLOB:
                out.append((n, left, top, right, bottom))
    out.sort(reverse=True)
    return out


def erase_text(img: Image.Image, box: tuple[float, float, float, float]) -> None:
    """글자 자리를 좌우 이웃 색으로 메운다. 바가 가로 그라디언트라 줄마다 잇는다."""
    w, h = img.size
    x0 = int(box[0] / 100 * w)
    y0 = int(box[1] / 100 * h)
    x1 = int(box[2] / 100 * w)
    y1 = int(box[3] / 100 * h)
    px = img.load()
    span = max(1, x1 - x0)
    for y in range(max(0, y0), min(h, y1)):
        left = px[max(0, x0 - 3), y]
        right = px[min(w - 1, x1 + 3), y]
        for x in range(max(0, x0), min(w, x1)):
            t = (x - x0) / span
            px[x, y] = tuple(
                int(left[c] + (right[c] - left[c]) * t) for c in range(4)
            )


def punch(veggie: str) -> dict | None:
    src = ART / f"{veggie}-card.webp"
    if not src.exists():
        print(f"{veggie}: 완성 카드가 없다 ({src.name})")
        return None
    img = Image.open(src).convert("RGBA")
    w, h = img.size

    found = blobs(img)

    # 생김새로 먼저 거른다. 넓이만 보면 배경 덩어리가 늘 이긴다.
    def facelike(blob):
        _, l, t, r, b = blob
        rx, ry = (r - l) / 2, (b - t) / 2
        if ry <= 0:
            return False
        if not (FACE_R_MIN * w <= rx <= FACE_R_MAX * w):
            return False
        return FACE_ASPECT_MIN <= rx / ry <= FACE_ASPECT_MAX

    cands = [b for b in found if facelike(b)]
    if len(cands) < 2:
        print(f"{veggie}: 얼굴 모양 덩어리를 둘 못 찾았다 ({len(cands)}개).")
        for n, l, t, r, b in found[:6]:
            print(f"    {n:>8} 화소  x{l}~{r} y{t}~{b}")
        return None

    # 아바타는 왼쪽 위 구석에 있다. 큰 얼굴은 남은 것 중 제일 큰 것.
    avatar = min(cands, key=lambda b: (b[1] + b[3]) / 2 + (b[2] + b[4]) / 2)
    face = max((b for b in cands if b is not avatar), key=lambda b: b[0])

    slots = {}
    hole = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(hole)
    for name, blob in (("face", face), ("avatar", avatar)):
        _, l, t, r, b = blob
        cx, cy = (l + r) / 2, (t + b) / 2
        rx, ry = (r - l) / 2 * SHRINK, (b - t) / 2 * SHRINK
        draw.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=255)
        slots[name] = {
            "cx": round(cx / w * 100, 2),
            "cy": round(cy / h * 100, 2),
            "rx": round(rx / w * 100, 2),
            "ry": round(ry / h * 100, 2),
        }

    hole = hole.filter(ImageFilter.GaussianBlur(max(1, int(min(w, h) * FEATHER * 0.06))))

    for box in TEXT_SLOTS.values():
        erase_text(img, box)

    # **알파에서 구멍만큼 뺀다.** 덮어쓰면 원래 투명하던 바깥까지 불투명해진다.
    alpha = img.getchannel("A")
    a, hl = alpha.load(), hole.load()
    out = Image.new("L", (w, h))
    o = out.load()
    for y in range(h):
        for x in range(w):
            o[x, y] = max(0, a[x, y] - hl[x, y])
    img.putalpha(out)

    dst = ART / f"{veggie}-card-slots.webp"
    img.save(dst, "WEBP", quality=92, method=6)
    print(f"{dst.name}  {dst.stat().st_size:,} bytes  ({w}x{h})")
    slots["name"] = dict(zip(("x0", "y0", "x1", "y1"), TEXT_SLOTS["name"]))
    slots["code"] = dict(zip(("x0", "y0", "x1", "y1"), TEXT_SLOTS["code"]))
    return slots


def main() -> None:
    result = {}
    for veggie in CARDS:
        got = punch(veggie)
        if got:
            result[veggie] = got
    print()
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
