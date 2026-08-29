#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow"]
# ///
"""
저쪽 레포에서 받은 에셋 묶음을 안드로이드 리소스로 들여온다.

frankie516c/dog-training-rag 의 `ui-experiments/main-screen/assets` 아래에서
테마 팩과 견종 워크 시트를 받아오면, 이 스크립트가 세 가지를 한 번에 한다.

  0. **강아지 시트 조각 털기** — 칸마다 몸에서 떨어진 발·다리 조각이 섞여 오는
     경우가 있다. 칸에서 가장 큰 덩어리만 남긴다.
  1. **방 그림 컷아웃** — 테마의 `room.png` 는 알파가 없는 불투명 그림이다.
     바깥을 뚫고 테두리를 두른다. 소품과 강아지는 이미 투명해서 건드리지 않는다.

     실루엣은 **참조 방 한 장에서만 구해** 전 테마에 씌운다. 파스텔 테마는 벽 색이
     배경색과 거의 같아서 색으로 가르면 벽이 통째로 뜯긴다. 테마는 같은 그림을
     리컬러한 것이라 실루엣이 전부 같으므로, 배경과 벽이 뚜렷이 다른 원본
     (`reference-room.png`, 황토색 배경)에서 한 번 구하면 된다.
  2. **WebP 변환** — PNG 그대로 넣으면 55MB 다. 손실 WebP(q92)면 6MB 로 줄고,
     2배 확대해 비교해도 눈에 띄는 차이가 없다. 부드럽게 셰이딩된 그림이라
     손실 압축이 잘 먹는다. (2색 도트였다면 무손실을 써야 한다)
  3. **이름 짓기** — 안드로이드 리소스 이름은 소문자·숫자·밑줄만 된다.
     `themes/sky-blue/rug-cream.png` -> `theme_sky_blue_rug_cream.webp`

사용:
    uv run tools/import_room_assets.py <받은폴더> [출력폴더]

받은폴더 구조는 저쪽 레포와 같아야 한다.

    <받은폴더>/themes/<테마>/{room,ball,basket,bowls,cabinet,doghouse,plant,rug,rug-cream}.png
    <받은폴더>/dogs/<견종>.png
    <받은폴더>/portraits/<견종>.png      (없어도 된다)
    <받은폴더>/window/<시간>_<날씨>.png  (없어도 된다)
    <받은폴더>/door/<시간>_<날씨>.png    (없어도 된다)

출력폴더 기본값은 `app/src/main/res/drawable-nodpi` 다.
"""

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from room_cutout import apply_mask, mask_of, strip_loose_bits  # noqa: E402

# 손실 압축 품질. 92 는 원본과 육안 차이가 없으면서 10분의 1 로 줄어드는 지점이다.
WEBP_QUALITY = 92

# 인코딩 노력. 6 이 가장 느리고 가장 작다. 한 번 굽고 마는 파일이라 느려도 된다.
WEBP_METHOD = 6

DEFAULT_OUT = Path("app/src/main/res/drawable-nodpi")


def resource_name(*parts: str) -> str:
    """안드로이드 리소스 이름 규칙: 소문자·숫자·밑줄."""
    joined = "_".join(parts).lower()
    return "".join(c if c.isalnum() else "_" for c in joined)


def to_webp(src: Path, dst: Path) -> tuple[int, int]:
    image = Image.open(src).convert("RGBA")
    # 알파가 처음부터 끝까지 불투명하면 채널을 버린다. 프로필 그림이 그런
    # 경우인데, 쓸모없는 알파를 남기면 파일이 근거 없이 커진다. 방·소품·강아지
    # 시트는 진짜 알파가 있으므로 이 가지에 걸리지 않는다.
    if image.getchannel("A").getextrema()[0] == 255:
        image = image.convert("RGB")
    dst.parent.mkdir(parents=True, exist_ok=True)
    image.save(dst, "WEBP", quality=WEBP_QUALITY, method=WEBP_METHOD)
    return src.stat().st_size, dst.stat().st_size


def main(drop: Path, out: Path) -> None:
    before = after = 0
    made = 0

    # 세 갈래가 다 있어야 하는 건 아니다. 프로필만, 강아지만 새로 받는 일이
    # 흔하므로 있는 것만 굽는다.
    theme_root = drop / "themes"
    themes = sorted(p for p in theme_root.iterdir() if p.is_dir()) if theme_root.is_dir() else []

    # 참조 실루엣은 **방 그림을 구울 때만** 쓴다. 처음엔 themes/ 가 있으면 무조건
    # 요구했는데, 그러면 소품 한 장만 새로 반입할 때도 참조 방을 내놓으라고 막아선다
    # (턴테이블을 넣다 걸렸다). 쓸 일이 생기면 그때 한 번 읽는다.
    silhouette: object | None = None

    def room_silhouette():
        nonlocal silhouette
        if silhouette is None:
            reference = drop / "reference-room.png"
            if not reference.exists():
                raise SystemExit(
                    f"참조 방이 없다: {reference}\n"
                    "배경과 벽이 뚜렷이 다른 원본 방 PNG 가 필요하다."
                )
            silhouette = mask_of(str(reference))
        return silhouette

    for theme in themes:
        for png in sorted(theme.glob("*.png")):
            staged = png
            if png.stem == "room":
                # 방만 불투명하다. 참조 실루엣을 씌우고 테두리를 둘러 임시 파일로.
                staged = png.with_name("room.cut.png")
                apply_mask(str(png), str(staged), room_silhouette())
            name = resource_name("theme", theme.name, png.stem)
            a, b = to_webp(staged, out / f"{name}.webp")
            before += png.stat().st_size
            after += b
            made += 1
            if staged != png:
                staged.unlink()

    for png in sorted((drop / "dogs").glob("*.png")) if (drop / "dogs").is_dir() else []:
        # 시트에 몸과 떨어진 조각이 섞여 오는 칸이 있다. 하필 정지 프레임에 붙으면
        # 강아지가 멈출 때마다 앞에 점이 떠 있는다.
        staged = png.with_name(f"{png.stem}.clean.png")
        if strip_loose_bits(str(png), str(staged)) == 0:
            staged.unlink()
            staged = png
        name = resource_name("dog", png.stem)
        a, b = to_webp(staged, out / f"{name}.webp")
        before += png.stat().st_size
        after += b
        made += 1
        if staged != png:
            staged.unlink()

    # 프로필 얼굴 그림. 걷기 시트와 달리 한 장짜리 불투명 그림이라 조각 털기도
    # 컷아웃도 필요 없다. 그대로 굽기만 한다.
    portraits = drop / "portraits"
    if portraits.is_dir():
        for png in sorted(portraits.glob("*.png")):
            name = resource_name("dog", png.stem, "portrait")
            _, b = to_webp(png, out / f"{name}.webp")
            before += png.stat().st_size
            after += b
            made += 1

    # 창밖 풍경. 유리 모양으로 잘린 알파 PNG 라 컷아웃도 조각 털기도 필요 없다.
    # 방 그림 위에 얹는 것이라 테마를 안 탄다 — 창틀·창살만 테마색이고 유리 안은
    # 여섯 테마가 같은 그림이기 때문이다.
    window = drop / "window"
    if window.is_dir():
        for png in sorted(window.glob("*.png")):
            name = resource_name("window", png.stem)
            _, b = to_webp(png, out / f"{name}.webp")
            before += png.stat().st_size
            after += b
            made += 1

    # 문밖 풍경. 아치와 기울어진 밑변은 **굽지 않는다** — 그리는 쪽이 `doorPath` 로
    # 오려내므로 네모 그대로 넣는다. 구워 넣으면 문 윤곽이 두 군데가 되어 어긋난다.
    door = drop / "door"
    if door.is_dir():
        for png in sorted(door.glob("*.png")):
            name = resource_name("door", png.stem)
            _, b = to_webp(png, out / f"{name}.webp")
            before += png.stat().st_size
            after += b
            made += 1

    if made == 0:
        raise SystemExit(
            f"구울 게 없다: {drop} 아래에 themes/ · dogs/ · portraits/ · window/ · door/ 가 없다."
        )

    print(f"{made}개  {before / 1e6:.1f}MB -> {after / 1e6:.1f}MB  ({after / before * 100:.0f}%)")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        raise SystemExit(2)
    main(Path(sys.argv[1]), Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_OUT)
