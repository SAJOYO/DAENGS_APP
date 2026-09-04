#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10", "numpy>=1.26"]
# ///
"""챗봇 대기 말풍선이 쓸 **견종별 "곰곰이" 얼굴**을 반입한다.

    uv run tools/import_thinking_portraits.py <드롭폴더>            # 미리 보기 + 확인 시트
    uv run tools/import_thinking_portraits.py <드롭폴더> --apply    # 반입

## 왜 따로 있나

챗봇이 답을 만드는 동안 화면에 글자 한 줄뿐이라 멈춘 것과 구분이 안 됐다. 기본
얼굴과 이 곰곰이 얼굴을 번갈아 그려 모션처럼 만든다 — 그림이 움직이면 "돌고 있다" 가
글자 없이도 읽힌다.

**말풍선 왼쪽 얼굴은 안 바꾼다.** 거기는 학사모 쓴 똑똑이(`smartRes`)이고 누가 말하는
지를 가리키는 자리다. 바뀌는 것은 말풍선 **안**이다.

## `import_smart_portraits.py` 와 형제다

규격(256x256 WebP q92)·여백·확인 시트가 같다. 두 벌로 갈라 두는 이유는 파일 이름
규칙과 매핑 표가 다르기 때문이다 — 저쪽이 판마다 다른 이름으로 준다.

## 이름이 둘 어긋나 있다

받은 파일은 `곰곰이_<영문키>.png` 라 대부분 그대로 앱 키와 맞는데 **둘이 다르다.**
후보가 하나씩뿐이라 짝은 분명하지만, 똑똑이 판에서 닥스훈트 셋이 치와와로 붙어 있던
적이 있으므로(`import_smart_portraits.py` 주석) **여기서도 표에 못 박는다.**

## 여백이 필요하다

아바타는 원으로 잘리는데(`ui/DogAvatar.kt`) 그림이 바깥 4% 테두리까지 차 있다
(재보니 견종에 따라 5~27%). [PAD] 만큼 덧대 원 안으로 밀어 넣는다. 덧대는 색은
**모서리 화소에서 뽑는다** — 배경색을 상수로 박으면 저쪽이 톤을 바꿀 때 띠가 생긴다.
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

# 파일 이름의 영문 조각 → `DogBreed` 의 키.
#
# **대부분 그대로다.** 여기 적는 것은 어긋난 둘뿐이고, 나머지는 이름이 곧 키다.
# 표에 없는 이름이 오면 그대로 쓰되, 앱에 없는 키면 아래에서 걸린다.
ALIASES = {
    # 앱에는 닥스훈트가 세 종이라 수식어가 필요하다. 파일에는 색만 있다.
    "short_black": "dachshund_short_black",
    # 앱 키는 `_tan` 까지 붙는다.
    "pomeranian_black": "pomeranian_black_tan",
}

# 앱이 아는 견종 스물일곱. `DogShapes.kt` 의 `DogBreed` 와 같아야 한다.
BREEDS = {
    "beagle", "bichon_frise", "border_collie", "chihuahua",
    "dachshund_long_beige", "dachshund_short_black", "dachshund_short_brown",
    "french_bulldog", "golden_retriever", "japanese_spitz", "jindo",
    "labrador_retriever", "maltese", "pomeranian_beige", "pomeranian_black_tan",
    "pomeranian_white", "pug", "schnauzer", "shiba_inu_beige", "shiba_inu_black",
    "shiba_inu_orange", "siberian_husky", "toy_poodle_chocolate",
    "toy_poodle_light_brown", "toy_poodle_silver", "welsh_corgi",
    "yorkshire_terrier",
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
        # 96px = 크게 볼 때 · 38px = 헤더 · 28px = 대기 말풍선 안
        for j, px in enumerate((96, 38, 28)):
            face = img.resize((px, px), Image.LANCZOS).convert("RGBA")
            mask = Image.new("L", (px, px), 0)
            ImageDraw.Draw(mask).ellipse((0, 0, px - 1, px - 1), fill=255)
            face.putalpha(mask)
            out.paste(face, (cx + (0 if j == 0 else 100), cy + 14 + (0 if j < 1 else (j - 1) * 44)), face)
    out.save(path)
    print(f"\n확인 시트 → {path}")
    print("  왼쪽 96px · 오른쪽 위 38px · 오른쪽 아래 28px(대기 말풍선)")


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    apply = "--apply" in sys.argv
    if not args:
        raise SystemExit("곰곰이 PNG 가 든 폴더를 달라")
    drop = pathlib.Path(args[0]).expanduser()

    found = sorted(drop.glob("곰곰이*.png"))
    if not found:
        raise SystemExit(f"곰곰이*.png 가 없다: {drop}")

    made, missing, unknown = [], set(BREEDS), []
    for src in found:
        raw = src.stem.replace("곰곰이_", "", 1).replace("곰곰이", "", 1)
        key = ALIASES.get(raw, raw)
        if key not in BREEDS:
            unknown.append(f"{src.name} → {key}")
            continue
        missing.discard(key)
        img = convert(src, PAD)
        made.append((key, img))
        dst = RES / f"dog_{key}_thinking.webp"
        if apply:
            img.save(dst, "WEBP", quality=QUALITY, method=6)
        print(f"{raw:24s} → {dst.name:40s} "
              f"{src.stat().st_size / 1e6:5.1f}MB → "
              f"{dst.stat().st_size / 1024 if dst.exists() else 0:5.1f}KB")

    if unknown:
        print("\n⚠ 앱에 없는 견종:", ", ".join(unknown))
    if missing:
        print("\n⚠ 못 찾은 견종:", ", ".join(sorted(missing)))
    if unknown or missing:
        raise SystemExit("매핑이 안 맞는다. 표를 고치고 다시 돌린다.")

    print(f"\n{len(made)}/{len(BREEDS)} 매핑됐다.")
    sheet(made, drop / "thinking-portraits-review.png")
    if not apply:
        print("\n--apply 를 안 줘서 파일은 안 썼다.")


if __name__ == "__main__":
    main()
