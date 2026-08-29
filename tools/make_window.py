# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow"]
# ///
"""창밖 풍경 여섯 벌을 굽는다 — 낮·밤 x 해·비·눈.

    uv run tools/make_window.py <드롭폴더>
    uv run tools/import_room_assets.py <드롭폴더>

앞엣것이 `<드롭폴더>/window/<시간>_<날씨>.png` 를 만들고, 뒤엣것이 구워서
`window_<시간>_<날씨>.webp` 로 넣는다.

## 왜 이렇게 만드나

창밖은 방 그림(1122x1402)에 구워져 있고 따로 떼어 둔 원본이 없다. 그래서
**있는 그림에서 유리만 오려 그것을 다시 칠한다.**

유리 마스크는 손으로 안 잰다. 테마 6종을 겹쳐 **전부 일치하는 화소가 곧 유리**다
(창틀·창살·벽은 테마마다 색이 다르므로 저절로 걸러진다).

층은 색으로 가른다. 픽셀 아트라 팔레트가 좁아 잘 갈린다.

    하늘 46.5%   나무 42.3%   구름 9.6%   먼 산 1.6%

## 다시 칠하는 방식

색을 통째로 갈아치우지 않고 **HLS 로 옮긴다.** 원래 그림의 명암 변화(그라데이션·
붓자국·픽셀 결)를 그대로 두고 색조와 밝기만 밀어야 화풍이 안 깨진다.

## 눈은 나무를 갈아야 한다

잎 달린 나무에 눈이 오면 계절이 어긋난다. 그래서 눈 두 벌은 나무 자리를 배경으로
덮은 뒤 **가지를 새로 그린다**(재귀 분기). 잎이 없으니 하늘이 훨씬 많이 보이는데,
그게 겨울이다.
"""
import colorsys
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOM = Path(r"C:\Users\804\Documents\workspace\DAENGS_APP\app\src\main\res\drawable-nodpi")
THEMES = ["sage", "cherry_blossom", "mint", "lavender", "sky_blue", "butter"]
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "window"); OUT.mkdir(parents=True, exist_ok=True)


# ---------------------------------------------------------------- 유리 마스크
# 창문이 있는 벽 조각. `RoomShellShapes.kt` 가 벽을 셋으로 나눠 둔 것을 따른다
#     문 6.8~17.8% · **창문 32.3~49.1%** · 액자 67.4~96.6%
# 넉넉히 잡고 실제 경계는 재서 찾는다.
SEARCH = (0.30, 0.14, 0.52, 0.60)


def glass_mask(rooms):
    """테마끼리 **안 변하는** 화소가 유리다. 창틀·창살·벽은 테마색이라 변한다.

    **창문 조각 안에서만 봐야 한다.** 방 전체로 보면 벽·바닥도 테마마다 변해서
    경계 상자가 방 전체가 되고, 그러면 안 변하는 화소가 창유리 말고도 걸린다.
    """
    from PIL import ImageChops
    base = rooms[0]
    W, H = base.size
    win = (round(SEARCH[0] * W), round(SEARCH[1] * H),
           round(SEARCH[2] * W), round(SEARCH[3] * H))
    crops = [r.crop(win) for r in rooms]
    diff = Image.new("L", crops[0].size, 0)
    for other in crops[1:]:
        diff = ImageChops.lighter(diff, ImageChops.difference(crops[0], other).convert("L"))
    varies = diff.point(lambda v: 255 if v > 12 else 0)
    inside = Image.new("L", crops[0].size, 0)
    inside.paste(255, varies.getbbox())         # 창틀이 둘러싼 범위
    glass = ImageChops.multiply(ImageChops.invert(varies), inside)
    glass = glass.filter(ImageFilter.MinFilter(3))   # 창살에 붙은 반화소를 깎는다
    full = Image.new("L", base.size, 0)
    full.paste(glass, (win[0], win[1]))
    return full


rooms = [Image.open(ROOM / f"theme_{t}_room.webp").convert("RGB") for t in THEMES]
mask_full = glass_mask(rooms)
BB = mask_full.getbbox()
base = rooms[0].crop(BB)
mask = mask_full.crop(BB)
SW, SH = base.size
RW, RH = rooms[0].size
print(f"방 {RW}x{RH} · 유리 {BB} ({SW}x{SH})")
print(f"백분율  x {BB[0]/RW*100:.2f} ~ {BB[2]/RW*100:.2f}   y {BB[1]/RH*100:.2f} ~ {BB[3]/RH*100:.2f}")


# ---------------------------------------------------------------- 층 가르기
def layer_of(c):
    r, g, b = c
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    if s < 0.18 and l > 0.72:
        return "cloud"
    if g >= r and g >= b and (g - b) > 12:
        return "tree"
    if b > r and (b - r) > 10:
        return "sky" if l > 0.62 or s > 0.35 else "hill"
    if l > 0.78:
        return "cloud"
    return "hill"


bp, mp = base.load(), mask.load()
LAYER = [[None] * SW for _ in range(SH)]
for y in range(SH):
    for x in range(SW):
        if mp[x, y]:
            LAYER[y][x] = layer_of(bp[x, y])

tree_cells = [(x, y) for y in range(SH) for x in range(SW) if LAYER[y][x] == "tree"]
sky_cells = [(x, y) for y in range(SH) for x in range(SW) if LAYER[y][x] == "sky"]
print(f"층  하늘 {len(sky_cells)}  나무 {len(tree_cells)}")


# ---------------------------------------------------------------- 다시 칠하기
def shift(c, dh=None, dl=0.0, ds=1.0, lmul=1.0):
    """HLS 로 옮긴다. **원래 명암 변화를 남기려고** 곱하기·더하기만 쓴다."""
    r, g, b = (v / 255 for v in c)
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    if dh is not None:
        h = dh
    l = max(0.0, min(1.0, l * lmul + dl))
    s = max(0.0, min(1.0, s * ds))
    return tuple(round(v * 255) for v in colorsys.hls_to_rgb(h, l, s))


# 층별 변환. (색조, 밝기더하기, 채도곱, 밝기곱)
GRADE = {
    ("day", "clear"): None,                     # 지금 그림 그대로
    ("night", "clear"): {
        "sky":   (0.625, -0.02, 0.85, 0.34),
        "cloud": (0.640, -0.05, 0.55, 0.42),
        "tree":  (0.610, -0.02, 0.55, 0.30),
        "hill":  (0.630, -0.02, 0.60, 0.34),
    },
    ("day", "rain"): {
        "sky":   (0.575, +0.02, 0.22, 0.80),
        "cloud": (0.585, -0.06, 0.18, 0.80),
        "tree":  (0.300, -0.02, 0.45, 0.72),
        "hill":  (0.580, +0.00, 0.25, 0.78),
    },
    ("night", "rain"): {
        "sky":   (0.615, -0.02, 0.45, 0.30),
        "cloud": (0.620, -0.04, 0.35, 0.34),
        "tree":  (0.320, -0.02, 0.40, 0.26),
        "hill":  (0.615, -0.02, 0.40, 0.30),
    },
    ("day", "snow"): {
        "sky":   (0.585, +0.10, 0.14, 0.92),
        "cloud": (0.590, +0.04, 0.10, 1.00),
        "tree":  (0.585, +0.10, 0.14, 0.92),     # 나무는 어차피 지운다
        "hill":  (0.590, +0.16, 0.10, 0.95),
    },
    ("night", "snow"): {
        "sky":   (0.625, +0.02, 0.35, 0.40),
        "cloud": (0.630, +0.02, 0.22, 0.50),
        "tree":  (0.625, +0.02, 0.35, 0.40),
        "hill":  (0.630, +0.04, 0.20, 0.48),
    },
}


def graded(grade):
    im = base.copy()
    if grade is None:
        return im
    p = im.load()
    for y in range(SH):
        for x in range(SW):
            k = LAYER[y][x]
            if k is None:
                continue
            dh, dl, ds, lmul = grade[k]
            p[x, y] = shift(bp[x, y], dh, dl, ds, lmul)
    return im


# ---------------------------------------------------------------- 겨울 나무
def bare_trees(im, night):
    """나무 자리를 배경으로 덮고 가지를 새로 그린다.

    나무 덩어리의 **아래쪽 가운데**에서 줄기를 올리고 재귀로 갈라진다. 가지 끝에는
    눈을 조금 얹는다 — 가지만 있으면 죽은 나무로 보인다.
    """
    p = im.load()
    # 1) 나무를 지운다.
    #
    # **열마다 따로 채우면 안 된다.** 처음에 같은 x 열에서 나무 바로 위 하늘색을
    # 끌어내렸더니, 나무 윗변이 열마다 달라서 열마다 다른 색이 내려와 **세로
    # 줄무늬**가 됐다. 하늘은 가로로 균일하고 세로로 변하므로 **행 단위로** 채운다.
    rows = {}
    for y in range(SH):
        vals = [p[x, y] for x in range(SW)
                if LAYER[y][x] in ("sky", "cloud") ]
        if vals:
            vals.sort(key=sum)
            rows[y] = vals[len(vals) // 2]          # 그 행 하늘의 중앙값
    # 하늘이 한 화소도 없는 행(나무가 가로로 꽉 찬 아래쪽)은 위에서 이어받는다
    last = None
    for y in range(SH):
        if y in rows:
            last = rows[y]
        elif last is not None:
            rows[y] = last
    rnd0 = random.Random(3)
    for x, y in tree_cells:
        c = rows.get(y)
        if c is None:
            continue
        n = rnd0.randint(-3, 3)                     # 밋밋한 면이 되지 않게 결을 조금
        p[x, y] = tuple(max(0, min(255, v + n)) for v in c)

    # 2) 눈 덮인 땅.
    #
    # 나무를 지우고 나니 아래쪽이 통째로 하늘이 되어 **물처럼** 보였다. 겨울 창밖은
    # 하늘만 있는 게 아니라 눈 덮인 땅이 있어야 한다. 나무가 가로로 3분의 1 이상을
    # 덮기 시작하는 높이를 지면으로 잡는다 — 원래 그림에서 숲이 시작되던 자리다.
    cover = [sum(1 for x in range(SW) if LAYER[y][x] == "tree") for y in range(SH)]
    wide = [sum(1 for x in range(SW) if LAYER[y][x] is not None) for y in range(SH)]
    ground = next((y for y in range(SH)
                   if wide[y] > 8 and cover[y] / wide[y] > 0.35), int(SH * 0.55))

    d = ImageDraw.Draw(im)
    rnd = random.Random(7)
    if night:
        snow_hi, snow_lo = (176, 190, 214), (128, 142, 168)
        bark, twig_snow = (24, 22, 30), (150, 165, 190)
    else:
        snow_hi, snow_lo = (246, 249, 253), (206, 218, 232)
        bark, twig_snow = (56, 42, 36), (240, 246, 252)

    # 지면 위쪽 가장자리는 조금 굽이친다. 자로 그은 듯 곧으면 물가처럼 보인다
    edge = [ground + round(math.sin(x / 17.0) * 3 + math.sin(x / 6.3) * 1.5) for x in range(SW)]
    for x in range(SW):
        for y in range(edge[x], SH):
            if LAYER[y][x] is None:
                continue
            t = (y - edge[x]) / max(1, SH - edge[x])
            c = tuple(round(a + (b - a) * t) for a, b in zip(snow_hi, snow_lo))
            n = rnd.randint(-4, 4)
            p[x, y] = tuple(max(0, min(255, v + n)) for v in c)

    # 3) 눈두덩과 덤불.
    #
    # 평평한 흰 면 하나로 두면 벌판이 아니라 **빈자리**로 보인다. 굽은 눈두덩으로
    # 명암을 주고 덤불 몇 개를 얹어야 땅으로 읽힌다.
    shade = tuple(round(a + (b - a) * 0.45) for a, b in zip(snow_hi, snow_lo))
    for i in range(7):
        dx = rnd.randrange(-20, SW + 20)
        dy = edge[min(max(dx, 0), SW - 1)] + rnd.randint(10, max(12, SH - ground - 20))
        dw = rnd.randint(40, 110)
        dh = rnd.randint(6, 14)
        for x in range(max(0, dx - dw), min(SW, dx + dw)):
            t = 1 - abs(x - dx) / dw
            for y in range(round(dy - dh * t), round(dy + 2)):
                if 0 <= y < SH and LAYER[y][x] is not None and y >= edge[x]:
                    p[x, y] = shade
    for i in range(9):
        bx = rnd.randrange(4, SW - 4)
        by = edge[min(max(bx, 0), SW - 1)] + rnd.randint(4, max(6, (SH - ground) // 2))
        if by >= SH or LAYER[by][bx] is None:
            continue
        r = rnd.randint(3, 7)
        d.ellipse([bx - r, by - r // 2, bx + r, by + r // 2], fill=shade)
        d.ellipse([bx - r, by - r // 2 - 1, bx + r, by + r // 3], fill=snow_hi)
        for k in range(3):                      # 눈 위로 삐죽 나온 잔가지
            a = math.pi / 2 + rnd.uniform(-0.7, 0.7)
            d.line([(bx, by - r // 2),
                    (bx + math.cos(a) * r, by - r // 2 - math.sin(a) * r)],
                   fill=bark, width=1)

    # 4) 먼 나무 줄. 지면이 흰 벌판 하나면 물가처럼 보인다. 지평선 바로 위에
    # 작고 흐린 가지 무리를 깔아 깊이를 준다.
    far = tuple(round(a + (b - a) * 0.62) for a, b in zip(bark, snow_hi))
    for i in range(26):
        fx = rnd.randrange(0, SW)
        fy = edge[min(max(fx, 0), SW - 1)] - rnd.randint(0, 3)
        fh = rnd.randint(6, 14)
        if LAYER[max(0, fy - fh)][fx] is None:
            continue
        d.line([(fx, fy), (fx, fy - fh)], fill=far, width=1)
        for k in range(3):
            a = math.pi / 2 + rnd.uniform(-0.9, 0.9)
            d.line([(fx, fy - fh * 0.6),
                    (fx + math.cos(a) * fh * 0.5, fy - fh * 0.6 - math.sin(a) * fh * 0.5)],
                   fill=far, width=1)

    # 5) 가지. 잎이 없으니 **잔가지가 촘촘해야** 죽은 나무로 안 보인다.
    # 처음엔 두 그루만 세웠더니 장대 두 개로 보였다 — 무리로 세운다.
    groups = {}
    for x, y in tree_cells:
        groups.setdefault("L" if x < SW * 0.45 else "R", []).append((x, y))

    def branch(x, y, ang, length, width, depth):
        if depth == 0 or length < 2:
            return
        x2 = x + math.cos(ang) * length
        y2 = y - math.sin(ang) * length
        d.line([(x, y), (x2, y2)], fill=bark, width=max(1, round(width)))
        if depth <= 2 and rnd.random() < 0.5:       # 잔가지 위에 눈
            d.line([(x, y - 1), (x2, y2 - 1)], fill=twig_snow, width=1)
        kids = 2 if depth > 3 else 3                # 끝으로 갈수록 더 갈라진다
        for i in range(kids):
            off = rnd.uniform(0.20, 0.55) * (1 if i % 2 else -1) + rnd.uniform(-0.12, 0.12)
            branch(x2, y2, ang + off, length * rnd.uniform(0.64, 0.80),
                   width * 0.68, depth - 1)

    def tree(cx, base_y, h, scale=1.0):
        trunk = h * 0.30
        d.line([(cx, base_y), (cx, base_y - trunk)], fill=bark,
               width=max(2, round(h * 0.032 * scale)))
        branch(cx, base_y - trunk, math.pi / 2, h * 0.34,
               max(2, h * 0.036 * scale), 7)

    for key, cells in groups.items():
        xs = [c[0] for c in cells]; ys = [c[1] for c in cells]
        x0, x1 = min(xs), max(xs)
        top_y = min(ys)
        cx = (x0 + x1) // 2
        gy = min(SH - 1, edge[min(max(cx, 0), SW - 1)] + 6)
        h = max(30, gy - top_y)
        # 큰 나무 하나 + 옆에 작은 것 둘. 한 그루만 세우면 장대가 된다
        tree(cx, gy, h)
        for dx, k in ((-0.30, 0.58), (0.34, 0.66)):
            nx = round(cx + (x1 - x0) * dx)
            if 2 <= nx < SW - 2:
                ny = min(SH - 1, edge[nx] + 5)
                tree(nx, ny, h * k, 0.8)
    return im


# ---------------------------------------------------------------- 비 · 눈 · 별
def add_rain(im, night):
    """빗줄기 — **가늘게 · 일자로 · 길게.**

    처음엔 길이 7~16px 에 0.28 만큼 비스듬히 그었더니 **눈처럼 보였다.** 창문이
    화면에서 폭 101px 이라 원본이 0.43 배로 줄어든다 — 7~16px 이 3~7px 점이 되고,
    점은 눈이다.

    세 가지가 다 필요했다.

        길게   27~57px. 줄어들어도 12~24px 로 남아 "내리는 것"으로 읽힌다
        일자   기울기 0. 비스듬하면 눈보라나 유리 흠집으로 보인다
        얇게   전부 1px. 굵게 하면 유리에 붙은 빗금이지 지나가는 비가 아니다

    앞뒤는 굵기 대신 **밝기**로 가른다. 굵기로 가르면 굵은 줄이 다시 빗금이 된다.
    """
    d = ImageDraw.Draw(im, "RGBA")
    rnd = random.Random(11)
    base_a = 150 if not night else 118

    # **자리를 균등 난수로 뽑으면 뭉친다.** 난수는 고르게 흩어지는 게 아니라 같은
    # 자리에 여러 개가 겹치는 성질이 있다(빈 데는 비고 어떤 데는 다발이 된다).
    # 판을 칸으로 나눠 **칸마다 한 줄씩** 놓고 칸 안에서만 흔든다. 고르면서도
    # 격자처럼 규칙적이지 않다.
    COLS, ROWS = 26, 8
    cw, ch = SW / COLS, (SH + 70) / ROWS
    for r in range(ROWS):
        for c in range(COLS):
            x = round((c + rnd.random()) * cw)
            y = round((r + rnd.random()) * ch) - 70
            ln = rnd.randint(27, 57)
            a = round(base_a * rnd.choice((1.0, 0.72, 0.48)))   # 앞 · 중간 · 뒤
            d.line([(x, y), (x, y + ln)], fill=(234, 244, 254, a), width=1)
    return im


def add_snow(im, night):
    d = ImageDraw.Draw(im, "RGBA")
    rnd = random.Random(13)
    a = 225 if not night else 190
    for _ in range(240):
        x = rnd.randrange(0, SW)
        y = rnd.randrange(0, SH)
        r = rnd.choice([0, 0, 1, 1, 2])
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, a))
    return im


def add_night_sky(im):
    """별과 달. 하늘로 판정된 자리에만 놓는다 — 나무 위에 별이 뜨면 안 된다."""
    d = ImageDraw.Draw(im, "RGBA")
    rnd = random.Random(5)
    pool = [c for c in sky_cells if c[1] < SH * 0.62]
    for x, y in rnd.sample(pool, min(150, len(pool))):
        b = rnd.randint(150, 255)
        d.point((x, y), fill=(b, b, min(255, b + 12), rnd.randint(120, 255)))
    # 달 — 오른쪽 위 하늘에.
    #
    # 처음엔 원을 그리고 그 위에 **투명한 원**을 얹어 초승달을 만들었는데, 투명을
    # 얹으면 하늘까지 뚫려서 검은 구멍이 됐다 (알파를 지우는 것이지 덮는 게 아니다).
    # 보름달로 간다 — 이 크기(지름 22px)에서는 초승달 모양이 어차피 안 읽힌다.
    top = [c for c in pool if c[1] < SH * 0.32 and c[0] > SW * 0.50]
    if top:
        mx, my = min(top, key=lambda c: (c[1], -c[0]))
        my += 20
        for r, a in ((17, 26), (14, 40), (11, 70)):     # 달무리
            d.ellipse([mx - r, my - r, mx + r, my + r], fill=(226, 236, 255, a))
        d.ellipse([mx - 9, my - 9, mx + 9, my + 9], fill=(250, 249, 236, 250))
        d.ellipse([mx - 4, my - 5, mx + 1, my + 0], fill=(232, 230, 214, 255))
        d.ellipse([mx + 1, my + 2, mx + 5, my + 6], fill=(236, 234, 219, 255))
    return im


# ---------------------------------------------------------------- 굽기
VARIANTS = [
    ("day", "clear"), ("night", "clear"),
    ("day", "rain"), ("night", "rain"),
    ("day", "snow"), ("night", "snow"),
]

for when, weather in VARIANTS:
    im = graded(GRADE[(when, weather)]).convert("RGB")
    night = when == "night"
    if weather == "snow":
        im = bare_trees(im, night)
    if night:
        im = add_night_sky(im.convert("RGBA")).convert("RGB")
    if weather == "rain":
        im = add_rain(im.convert("RGBA"), night).convert("RGB")
    elif weather == "snow":
        im = add_snow(im.convert("RGBA"), night).convert("RGB")

    out = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    dst = OUT / "window"
    dst.mkdir(parents=True, exist_ok=True)
    out.save(dst / f"{when}_{weather}.png")
    print(f"  window/{when}_{weather}.png")

# 확인용 대조표
sheet = Image.new("RGB", (SW * 3 + 24, SH * 2 + 12), (16, 22, 30))
for i, (when, weather) in enumerate(VARIANTS):
    im = Image.open(OUT / "window" / f"{when}_{weather}.png").convert("RGBA")
    flat = Image.new("RGB", (SW, SH), (16, 22, 30))
    flat.paste(im, (0, 0), im)
    sheet.paste(flat, ((i // 2) * (SW + 12), (i % 2) * (SH + 12)))
sheet.save(OUT / "_sheet.png")
print(f"\n-> {OUT}/_sheet.png  (왼쪽부터 해·비·눈, 위 낮 / 아래 밤)")
