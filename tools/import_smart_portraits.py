#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10", "numpy>=1.26"]
# ///
"""챗봇이 쓸 **견종별 "똑똑이" 얼굴**을 반입한다.

    uv run tools/import_smart_portraits.py <드롭폴더>            # 미리 보기 + 확인 시트
    uv run tools/import_smart_portraits.py <드롭폴더> --apply    # 반입

## 왜 따로 있나

챗봇 화면 헤더에는 "댕스 AI" 라고 적혀 있는데 그 옆 얼굴이 **사용자의 강아지**였다.
내 개가 나에게 말을 거는 그림이라 누가 말하는 건지 흐렸다. 저쪽이 견종마다 학사모 쓴
"똑똑이" 판을 그려 와서, 챗봇은 **내 개와 같은 견종의, 그러나 확실히 다른** 얼굴을
갖는다 — 내 개는 아니지만 남의 개도 아니다.

## 이름이 한 번 어긋났었다

처음 받은 판에서는 닥스훈트 셋이 `똑똑이장모베이지치와와` 처럼 **치와와**로 붙어
있었다. 앱에는 치와와가 한 종, 닥스훈트가 세 종이라 그대로 믿으면 세 종이 밀린다.
지금 파일은 고쳐졌지만, 다시 어긋날 수 있으니 **매핑을 이 표에 못 박는다.**

## 2048px 을 그대로 넣지 않는다

받은 PNG 는 2048x2048 RGBA 로 한 장에 6MB, 스물일곱 장이면 163MB 다. 쓰이는 자리는
32~38dp 이므로 기존 초상화와 같은 **256x256 WebP** 로 줄인다 (0.4MB 남짓).

## 학사모가 원에 잘린다

아바타는 원으로 잘리는데(`ui/DogAvatar.kt`) 똑똑이는 학사모가 위쪽 끝까지 차 있다.
[PAD] 만큼 크림색으로 덧대 원 안으로 밀어 넣는다. 크림색은 **모서리 화소에서 뽑는다** —
배경색을 상수로 박으면 저쪽이 톤을 바꿀 때 테두리에 띠가 생긴다.
"""

from __future__ import annotations

import pathlib
import sys

import numpy as np
from PIL import Image

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"

SIZE = 256
QUALITY = 92
PAD = 0.06

# 한글 파일 이름 → `DogBreed` 의 키. 애매한 것만 옆에 적어 둔다.
BREEDS = {
    "비글": "beagle",
    "실버푸들": "toy_poodle_silver",
    "연한갈색푸들": "toy_poodle_light_brown",
    "초코푸들": "toy_poodle_chocolate",
    "말티즈": "maltese",
    "요크셔": "yorkshire_terrier",
    "치와와": "chihuahua",
    "비숑": "bichon_frise",
    "레브라도리트리버": "labrador_retriever",   # 표기가 "레브라도" 다
    "골든리트리버": "golden_retriever",
    "스피츠": "japanese_spitz",
    "진돗개": "jindo",
    "검은시바": "shiba_inu_black",
    "베이지시바": "shiba_inu_beige",
    "오렌지시바": "shiba_inu_orange",
    "허스키": "siberian_husky",
    "블랙탄포메": "pomeranian_black_tan",
    "베이지포메": "pomeranian_beige",
    "하얀포메": "pomeranian_white",
    "보더콜리": "border_collie",
    "웰시코기": "welsh_corgi",
    # 이 셋이 한때 "치와와" 로 붙어 있었다. 수식어(단모/장모 + 색)로 가른다.
    "단모갈색닥스훈트": "dachshund_short_brown",
    "단모검정닥스훈트": "dachshund_short_black",
    "장모베이지닥스훈트": "dachshund_long_beige",
    "불독": "french_bulldog",                    # 앱에 불독은 프렌치 한 종뿐이다
    "퍼그": "pug",
    "슈나우저": "schnauzer",
}


def corner_colour(im: Image.Image) -> tuple[int, int, int]:
    """네 모서리 8x8 의 가운뎃값. 배경색을 상수로 박지 않는다."""
    a = np.asarray(im.convert("RGB"), dtype=np.uint8)
    k = 8
    corners = np.concatenate([
        a[:k, :k].reshape(-1, 3), a[:k, -k:].reshape(-1, 3),
        a[-k:, :k].reshape(-1, 3), a[-k:, -k:].reshape(-1, 3),
    ])
    return tuple(int(v) for v in np.median(corners, axis=0))


def convert(src: pathlib.Path, pad: float) -> Image.Image:
    im = Image.open(src).convert("RGB")
    w, h = im.size
    m = int(round(max(w, h) * pad))
    canvas = Image.new("RGB", (w + m * 2, h + m * 2), corner_colour(im))
    canvas.paste(im, (m, m))
    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


def sheet(made: list[tuple[str, Image.Image]], path: pathlib.Path) -> None:
    """**화면 크기에서 본다.** 원본으로만 보면 32dp 에서 뭉개지는 것을 못 잡는다."""
    from PIL import ImageDraw
    cols = 7
    cell = 132
    rows = (len(made) + cols - 1) // cols
    out = Image.new("RGB", (cols * cell + 12, rows * (cell + 18) + 8), (18, 18, 22))
    d = ImageDraw.Draw(out)
    for i, (key, img) in enumerate(made):
        cx = (i % cols) * cell + 8
        cy = (i // cols) * (cell + 18) + 6
        d.text((cx, cy), key[:18], fill=(170, 200, 230))
        # 96px = 확대 뷰, 38px = 챗봇 헤더, 32px = 말풍선
        for j, px in enumerate((96, 38, 32)):
            face = img.resize((px, px), Image.LANCZOS).convert("RGBA")
            mask = Image.new("L", (px, px), 0)
            ImageDraw.Draw(mask).ellipse((0, 0, px - 1, px - 1), fill=255)
            face.putalpha(mask)
            out.paste(face, (cx + (0 if j == 0 else 100), cy + 14 + (0 if j < 1 else (j - 1) * 44)), face)
    out.save(path)
    print(f"\n확인 시트 → {path}")
    print("  왼쪽 96px · 오른쪽 위 38px(헤더) · 오른쪽 아래 32px(말풍선)")


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    apply = "--apply" in sys.argv
    if not args:
        raise SystemExit("똑똑이 PNG 가 든 폴더를 달라")
    drop = pathlib.Path(args[0]).expanduser()

    found = sorted(drop.glob("똑똑이*.png"))
    if not found:
        raise SystemExit(f"똑똑이*.png 가 없다: {drop}")

    made, missing, unknown = [], set(BREEDS), []
    for src in found:
        korean = src.stem.replace("똑똑이", "", 1)
        key = BREEDS.get(korean)
        if key is None:
            unknown.append(src.name)
            continue
        missing.discard(korean)
        img = convert(src, PAD)
        made.append((key, img))
        dst = RES / f"dog_{key}_smart.webp"
        if apply:
            img.save(dst, "WEBP", quality=QUALITY, method=6)
        print(f"{korean:20s} → {dst.name:38s} "
              f"{src.stat().st_size / 1e6:5.1f}MB → "
              f"{dst.stat().st_size / 1024 if dst.exists() else 0:5.1f}KB")

    if unknown:
        print("\n⚠ 표에 없는 파일:", ", ".join(unknown))
    if missing:
        print("\n⚠ 못 찾은 견종:", ", ".join(sorted(missing)))
    if unknown or missing:
        raise SystemExit("매핑이 안 맞는다. 표를 고치고 다시 돌린다.")

    print(f"\n{len(made)}/27 매핑됐다.")
    sheet(made, drop / "smart-portraits-review.png")
    if not apply:
        print("\n--apply 를 안 줘서 파일은 안 썼다.")


if __name__ == "__main__":
    main()
