#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow>=10", "numpy>=1.26", "scipy>=1.11"]
# ///
"""과일 카드에서 **흰색으로 칠해진 얼굴 자리 둘**을 뚫는다.

    uv run tools/punch_fruit_holes.py <드롭폴더>            # 미리 보기 + 검토 시트
    uv run tools/punch_fruit_holes.py <드롭폴더> --apply    # 뚫는다

## 왜 새 도구인가 — 기존 둘 다 못 쓴다

`punch_card_slots.py` 는 **은퇴한 도구**다. 대상이 `("cabbage", "sweet-potato")` 로
박혀 있고, 얼굴을 **크림색 색조**(hue 15~50°, 채도 ≥ 0.12)로 찾는다. 저쪽이 강아지
얼굴을 인쇄해 보내던 시절의 방식이라, 흰 표시 원은 채도가 0 이라 문턱에서 탈락한다.

`card_text_slots.py` 는 현행 주력이고 글자 지우기는 그쪽이 정본이다. 그런데 대상 목록을
`ART.glob("*-card-slots.webp")` 로 만들어서, **아직 판이 없는 새 벌은 통째로 건너뛴다.**

그래서 이 도구는 **그 앞 한 걸음만** 한다 — 구멍을 뚫어 판을 만들어 준다. 글자는 안
건드린다. 이 도구를 돌린 뒤 `card_text_slots.py` 를 돌리면, 그때는 판이 있으므로
그쪽이 이름·번호를 지우고 `Slot(...)` 을 재 준다.

    1) uv run tools/punch_fruit_holes.py <드롭폴더> --apply     ← 구멍
    2) uv run tools/card_text_slots.py  <드롭폴더> --apply     ← 글자

⚠️ **두 걸음의 입력이 같은 파일이어야 한다.** `card_text_slots.py` 는 나가 있는 판과
   드롭폴더 그림의 **크기가 같으면** RGB 만 갈아 끼우고 알파는 그대로 둔다. 크기가
   다르면 구멍을 자기가 다시 재는데, 그쪽은 **검은 얼굴창·흰 아바타**를 전제하므로
   과일(둘 다 흰색)에서는 틀린 답을 낸다.

## 자르지 않는다. 모서리도 안 오린다

처음엔 검은 여백을 자르고 둥근 모서리를 알파로 오리려 했는데, **야채 판이 이미 그렇지
않다.** `cabbage-card-slots.webp` 는 모서리 네 점이 전부 불투명한 검정이고 당근은 위
여백이 45px(3.1%) 있다. 투명한 것은 **구멍 둘뿐**이다(불투명 비율 88.9%). 과일 원화도
여백이 0~8px 라 같은 관례 안에 있다. 건드릴 이유가 없다.

## 얼굴창과 아바타를 **크기로 가르면 안 된다**

바나나는 얼굴창이 25,936px, 아바타가 20,047px 로 거의 같다. 크기로 정렬하면 그 한
장에서 둘이 뒤바뀌고, 화면에서는 "얼굴이 왼쪽 위 작은 원에만 들어간" 카드가 된다.
**자리로 가른다** — 아바타는 언제나 왼쪽 위다.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ART = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/neo-hologram/art"

# 표시 원이 흰색이다. 244 는 `card_text_slots.HOLE_LIGHT` 와 같은 값 — 두 도구가 같은
# "흰색" 을 봐야 한 카드를 두 걸음으로 처리해도 답이 안 갈린다.
WHITE = 244
# 찾은 원을 이만큼 키워 뚫는다. 표시 원 가장자리에 저쪽 안티에일리어싱이 한 겹 남는다.
# `card_text_slots.HOLE_GROW` 와 같은 값이다.
GROW = 1.03
# 이보다 작은 흰 덩어리는 반짝임이다. 카드 넓이 대비.
MIN_AREA = 0.002

# WebP 굽기 — 저장소 공통 (`import_room_assets.py` 에 근거가 적혀 있다).
WEBP = dict(quality=92, method=6)

# 잠긴 칸 표지(`<id>.webp`)는 **줄여서 굽는다.** 야채가 810~900px 로 들어와 있고
# (`carrot.webp` 900x1125), 화면에서는 그리드 한 칸 160dp 로 그려진다. 원본 크기로 두면
# 카드당 0.5MB 라 APK 가 12MB 늘어난다. 개인화할 판(`-card-slots`)은 얼굴을 끼워
# 확대까지 하므로 **안 줄인다.**
COVER_W = 900


def blobs(rgb: np.ndarray) -> list[dict]:
    """흰 덩어리를 큰 것부터. 각각 외접상자에서 중심·반지름을 잰다."""
    h, w, _ = rgb.shape
    white = (rgb > WHITE).all(axis=2)
    lab, n = ndimage.label(white)
    if n == 0:
        return []
    out = []
    for i, size in enumerate(ndimage.sum(white, lab, range(1, n + 1)), start=1):
        if size < w * h * MIN_AREA:
            continue
        ys, xs = np.nonzero(lab == i)
        out.append(dict(
            area=int(size),
            cx=(xs.min() + xs.max()) / 2, cy=(ys.min() + ys.max()) / 2,
            rx=(xs.max() - xs.min()) / 2, ry=(ys.max() - ys.min()) / 2,
        ))
    return sorted(out, key=lambda b: -b["area"])


def split(found: list[dict], w: int, h: int) -> tuple[dict, dict] | None:
    """둘 중 어느 것이 아바타인가. **자리로 가른다** (위 docstring 참고)."""
    if len(found) < 2:
        return None
    a, b = found[0], found[1]
    corner = lambda d: (d["cx"] / w) ** 2 + (d["cy"] / h) ** 2
    avatar, face = (a, b) if corner(a) < corner(b) else (b, a)
    return face, avatar


def punch(alpha: np.ndarray, hole: dict) -> None:
    """타원 하나를 알파에서 **뺀다.** 제자리에서 고친다.

    ⚠️ 덮어쓰지 않고 빼는 이유는 `punch_card_slots.py:267` 과 같다 — 덮어쓰면 원래
       투명하던 자리가 되살아난다. 지금 과일 원화는 알파가 없어 차이가 없지만,
       나중에 알파가 있는 원화가 오면 그때 갈린다.
    """
    h, w = alpha.shape
    yy, xx = np.ogrid[:h, :w]
    rx = max(hole["rx"] * GROW, 1.0)
    ry = max(hole["ry"] * GROW, 1.0)
    inside = ((xx - hole["cx"]) / rx) ** 2 + ((yy - hole["cy"]) / ry) ** 2 <= 1.0
    alpha[inside] = 0


def pct(hole: dict, w: int, h: int) -> str:
    return (f"Hole({hole['cx'] / w * 100:.2f}f, {hole['cy'] / h * 100:.2f}f, "
            f"{hole['rx'] / w * 100:.2f}f, {hole['ry'] / h * 100:.2f}f)")


def sheet(items: list[tuple[str, Image.Image, dict, dict]], path: pathlib.Path) -> None:
    """검토 시트. 초록=얼굴창 · 파랑=아바타. **저장소 밖에 쓴다.**"""
    if not items:
        return
    cols = 4
    tw = 320
    rows = (len(items) + cols - 1) // cols
    th = max(int(tw / (im.width / im.height)) for _, im, _, _ in items) + 22
    out = Image.new("RGB", (tw * cols, th * rows), (24, 24, 28))
    draw = ImageDraw.Draw(out)
    for i, (name, im, face, avatar) in enumerate(items):
        ox, oy = (i % cols) * tw, (i // cols) * th
        s = tw / im.width
        out.paste(im.convert("RGB").resize((tw, int(im.height * s))), (ox, oy + 22))
        draw.text((ox + 6, oy + 5), name, fill=(235, 235, 235))
        for hole, color in ((face, (60, 230, 120)), (avatar, (90, 170, 255))):
            draw.ellipse(
                [ox + (hole["cx"] - hole["rx"] * GROW) * s, oy + 22 + (hole["cy"] - hole["ry"] * GROW) * s,
                 ox + (hole["cx"] + hole["rx"] * GROW) * s, oy + 22 + (hole["cy"] + hole["ry"] * GROW) * s],
                outline=color, width=2)
    out.save(path)
    print(f"\n검토 시트 → {path}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("drop", type=pathlib.Path, help="<id>-card.png 이 든 폴더")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    sources = sorted(p for p in args.drop.iterdir()
                     if p.stem.endswith("-card") and p.suffix.lower() in (".png", ".webp", ".jpg"))
    if not sources:
        sys.exit(f"{args.drop} 에 <id>-card.png 이 없다")

    items, values = [], {}
    for src in sources:
        cid = src.stem[: -len("-card")]
        im = Image.open(src).convert("RGBA")
        rgb = np.asarray(im)[..., :3]
        w, h = im.size

        found = blobs(rgb)
        pair = split(found, w, h)
        if pair is None:
            print(f"{cid:16s} ⚠ 흰 덩어리를 둘 못 찾았다 (찾은 것 {len(found)}개)")
            continue
        face, avatar = pair
        third = found[2]["area"] / found[1]["area"] if len(found) > 2 else 0.0
        flag = "  ⚠ 3위 덩어리가 가깝다 — 시트를 꼭 볼 것" if third > 0.25 else ""

        print(f"{cid:16s} ratio {w / h:.3f}  face = {pct(face, w, h)}")
        print(f"{'':16s}              avatar = {pct(avatar, w, h)}{flag}")

        alpha = np.full((h, w), 255, np.uint8)
        punch(alpha, face)
        punch(alpha, avatar)
        out = np.dstack([rgb, alpha])
        items.append((cid, im, face, avatar))
        values[cid] = dict(
            ratio=round(w / h, 4),
            face=[round(face["cx"] / w * 100, 2), round(face["cy"] / h * 100, 2),
                  round(face["rx"] / w * 100, 2), round(face["ry"] / h * 100, 2)],
            avatar=[round(avatar["cx"] / w * 100, 2), round(avatar["cy"] / h * 100, 2),
                    round(avatar["rx"] / w * 100, 2), round(avatar["ry"] / h * 100, 2)],
        )

        if args.apply:
            # 잠긴 칸이 쓰는 완성 카드 — 글자가 인쇄된 그대로다. 줄여서 굽는다.
            cover = Image.fromarray(rgb)
            cover = cover.resize((COVER_W, round(COVER_W * h / w)), Image.LANCZOS)
            cover.save(ART / f"{cid}.webp", "WEBP", **WEBP)
            # 개인화할 판 — 구멍만 뚫었다. 글자는 card_text_slots.py 가 지운다.
            Image.fromarray(out).save(ART / f"{cid}-card-slots.webp", "WEBP", **WEBP)

    sheet(items, args.drop / "fruit-holes-review.png")
    print("\n" + json.dumps(values, ensure_ascii=False, indent=1))
    if args.apply:
        print(f"\n{len(values)}장을 {ART} 에 구웠다.")
        print("다음: uv run tools/card_text_slots.py <드롭폴더> --apply  ← 글자를 지운다")
    else:
        print("\n--apply 를 안 줘서 파일은 그대로다.")


if __name__ == "__main__":
    main()
