#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10"]
# ///
"""이머시브 **무대 주인공**의 얼굴 자리를 뚫는다.

    uv run tools/punch_subject_face.py            # 미리 보기
    uv run tools/punch_subject_face.py --apply    # 뚫는다

## 무대에도 우리 아이가 서야 한다

카드는 얼굴 자리를 뚫어 놨는데(`punch_card_slots.py`) 무대 주인공(`*-subject.webp`)은
저쪽 강아지 얼굴이 그대로 박혀 있었다. 그래서 "내 카드로 들어갔는데 무대에는 다른
개가 서 있는" 화면이 났다.

**전신 그림이 새로 필요한 게 아니다.** 야채 몸통은 그대로 쓰고 얼굴만 갈아 끼우면
된다 — 카드에서 한 것과 똑같다.

## 자리는 계산으로 나온다. 재지 않는다

두 값을 이미 갖고 있다. 카드 안에서 **얼굴 구멍**이 어디인지(`CardTemplate.face`)와,
카드 안에서 **누끼**가 어디인지(`ImmersiveScene.fit`)다. 둘 다 카드 크기 대비 % 라
나누면 누끼 기준 좌표가 된다.

    누끼 안 얼굴 cx = (카드 얼굴 cx - 누끼 x) / 누끼 w

앱에서는 `ImmersiveScene.faceInSubject()` 가 같은 식을 쓴다. **여기 값이 그 식과
어긋나면 얼굴이 엉뚱한 데 뜬다** — 그래서 이 파일에도 원본 두 값을 적어 두고 여기서
나눈다. 결과를 손으로 옮겨 적지 않는다.

## 뚫는 크기

카드 구멍보다 **조금 크게** 뚫는다. 야채가 얼굴을 둘러싼 그림이라 원래 얼굴이 구멍
가장자리로 삐져나오면 두 마리가 겹쳐 보인다. 가장자리는 부드럽게 흐린다 —
`punch_card_slots.py` 와 같은 이유다.
"""

from __future__ import annotations

import pathlib
import sys

from PIL import Image, ImageDraw, ImageFilter

ART = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/neo-hologram/art"

# 카드 안 얼굴 구멍 (ui/dogcard/CardSlots.kt 의 face)
CARD_FACE = {
    "cabbage": (52.84, 44.71, 19.81, 14.27),
    "sweet-potato": (48.84, 40.76, 12.13, 8.80),
    "lettuce": (50.00, 38.82, 9.97, 7.48),
}

# 카드 안 누끼 자리 (ui/dex/Immersive.kt 의 fit)
SUBJECT_FIT = {
    "cabbage": (6.06, 14.15, 87.43, 62.70),
    "sweet-potato": (11.52, 14.93, 80.02, 62.84),
    "lettuce": (11.22, 15.47, 80.33, 62.13),
}

# 카드 구멍보다 이만큼 키워 뚫는다. 원래 얼굴이 가장자리로 남으면 두 마리가 겹친다.
GROW = 1.18

# 구멍 가장자리를 흐리는 폭(짧은 반지름 대비).
FEATHER = 0.06


def face_in_subject(vid: str) -> tuple[float, float, float, float]:
    cx, cy, rx, ry = CARD_FACE[vid]
    x, y, w, h = SUBJECT_FIT[vid]
    return ((cx - x) / w * 100, (cy - y) / h * 100, rx / w * 100, ry / h * 100)


def punch(vid: str, apply: bool) -> None:
    path = ART / f"{vid}-subject.webp"
    if not path.exists():
        print(f"{vid:14s} 누끼가 없다 ({path.name})")
        return
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    cx, cy, rx, ry = face_in_subject(vid)
    px, py = w * cx / 100, h * cy / 100
    prx, pry = w * rx / 100 * GROW, h * ry / 100 * GROW

    # 구멍 마스크. 흰 곳이 남고 검은 곳이 뚫린다.
    mask = Image.new("L", (w, h), 255)
    ImageDraw.Draw(mask).ellipse(
        [px - prx, py - pry, px + prx, py + pry], fill=0
    )
    blur = max(1, int(min(prx, pry) * FEATHER))
    mask = mask.filter(ImageFilter.GaussianBlur(blur))

    alpha = im.getchannel("A")
    im.putalpha(Image.eval(Image.merge("L", (alpha,)), lambda v: v))
    im.putalpha(
        Image.composite(alpha, Image.new("L", (w, h), 0), mask)
    )

    print(
        f"{vid:14s} 구멍 cx={cx:.2f} cy={cy:.2f} rx={rx:.2f} ry={ry:.2f}"
        f"  → {int(prx * 2)}x{int(pry * 2)}px  (누끼 {w}x{h})"
    )
    if apply:
        im.save(path, "WEBP", quality=92, method=6)


def main() -> None:
    apply = "--apply" in sys.argv
    for vid in CARD_FACE:
        punch(vid, apply)
    if not apply:
        print("\n--apply 를 안 줘서 파일은 그대로다.")


if __name__ == "__main__":
    main()
