# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow"]
# ///
"""턴테이블(엘피) 소품을 그린다. 테마 6종.

    uv run tools/make_turntable.py <드롭폴더>
    uv run tools/import_room_assets.py <드롭폴더>

앞엣것이 `<드롭폴더>/themes/<테마>/turntable.png` 를 만들고, 뒤엣것이 구워서
`theme_<테마>_turntable.webp` 로 넣는다. **테마 폴더 이름은 리소스 접두어**다 —
`cherry_blossom`, `sky_blue` 다. 짧게 썼다가 `theme_cherry_turntable` 로 구워져
`RoomTheme.art()` 가 못 찾았다.

저쪽 레포의 소품은 `build_room_theme_assets.ps1` 이 리컬러하는데, 그 스크립트는
소품마다 손으로 쓴 규칙이 있어서 새 소품을 못 태운다. 그래서 이 저장소에 둔다.

## 참고 사진

사용자가 준 사진의 미드센추리 콘솔이다. 구조가 셋이다.

    다리   가늘고 아래로 갈수록 가늘어지는 네 다리. 밖으로 벌어져 있다
    뚜껑   뒤쪽 경첩에서 열려 뒤로 젖혀진 판. 안쪽 면(나뭇결)이 보인다
    그릴   앞면의 스피커 천. 몸통보다 밝고 짜임이 있다

처음에 다리를 뺐던 것은 가느다란 타원 기둥을 달았다가 카드 테이블처럼 떠서였다.
사진을 보니 **가는 다리가 원래 모양**이고, 문제는 다리가 아니라 그 그리는 법이었다.
사다리꼴로 좁혀 그리고 밖으로 벌린다.

## 규격

`MiniRoomModel.kt:102` 에 저쪽에 부탁하려던 사양이 남아 있다.

    자리      [5, 0]  뒷벽 창문 아래 (유일하게 빈 벽면)
    발자국    2 x 2 칸
    기준점    바닥에 닿는 점 — **다리 끝**이다. 몸통 밑면이 아니다

아트 상자 크기와 기준점은 다 그린 뒤 내용에 맞춰 다시 잡는다. 빈 여백을 상자에
남기면 **화면에서 그만큼 작게 그려진다**(ArtBox 크기가 곧 화면 크기다).

## 각도

`docs/asset-workflow.md`: 직교 투영, 타일 2:1.

  - 바닥에 닿는 둥근 것은 **가로:세로 2:1 타원** (플래터·판)
  - 상자는 가로 2 갈 때 세로 1 기울기
  - **높이는 절대 안 기울인다.** 다리도 위아래로 곧게 선다 (벌어짐은 좌우로만)

## 화풍 — 검은 테두리를 쓰지 않는다

기존 가구(서랍장·개집·바구니·화분)를 화면 크기로 놓고 보면

  - **검은 윤곽선이 없다.** 면끼리의 색조 차이로 형태가 선다
  - 면마다 부드러운 그라데이션이 있고 나뭇결·천 짜임 같은 질감이 얹혀 있다
  - 제일 어두운 색도 검정이 아니라 따뜻한 갈색·올리브다
  - 바닥 그림자를 굽지 않는다 (반투명 화소가 0~1% 뿐이다)

## 색

    서랍장   #CCA86C #D8B478 나무 · #FCE4A8 크림 상판 · #847830 올리브 손잡이
    개집     #6C783C #849048 올리브
    바구니   #A86C18 #B47818 호박색

사진은 짙은 월넛이지만 그대로 가져오면 파스텔 방에서 혼자 어둡다. **구조만
가져오고 색은 테마에서 굴린다.**
"""
import colorsys
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "turntable"); OUT.mkdir(parents=True, exist_ok=True)

W, H = 820, 980
ANCHOR = (W * 0.50, H * 0.860)      # 바닥에 닿는 점 = 다리 끝
SS = 3
MARGIN = 6
TARGET_W = 200.0

# 두 번째 칸은 **리소스 접두어**다. 테마 id 가 아니다 — `cherry-blossom` 은
# `theme_cherry_blossom_*`, `sky-blue` 는 `theme_sky_blue_*` 로 들어간다.
# 폴더 이름을 짧게 썼다가 theme_cherry_turntable 로 구워져서 art() 가 못 찾았다.
THEMES = [
    ("sage",           "sage",     "#EDD09F", "#D9A867", "#7F793A"),
    ("cherry-blossom", "cherry_blossom", "#F4D7DF", "#DBACB5", "#A95E76"),
    ("mint",           "mint",     "#D8EFE5", "#B1D2C0", "#5F9D85"),
    ("lavender",       "lavender", "#E7DCF2", "#BFB3D5", "#79629A"),
    ("sky-blue",       "sky_blue", "#DCEEF7", "#AED1E8", "#4F83AA"),
    ("butter",         "butter",   "#FFF0BD", "#E0C57E", "#B68737"),
]


def hex2rgb(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def shade(rgb, dl=0.0, ds=0.0):
    r, g, b = (v / 255 for v in rgb)
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    return tuple(round(v * 255) for v in colorsys.hls_to_rgb(
        h, max(0.04, min(0.97, l + dl)), max(0.0, min(1.0, s + ds))))


def palette(wall, floor, accent):
    fl, wa, ac = hex2rgb(floor), hex2rgb(wall), hex2rgb(accent)
    return {
        "deck_hi": shade(wa, +0.10), "deck_lo": shade(wa, -0.06),
        "wood_hi": shade(fl, +0.06), "wood_lo": shade(fl, -0.10),
        "side_hi": shade(fl, -0.08), "side_lo": shade(fl, -0.22),
        "lid_hi": shade(fl, -0.04), "lid_lo": shade(fl, -0.18),
        "lid_edge": shade(fl, -0.26, -0.04),
        "grille_hi": shade(wa, -0.02, -0.10), "grille_lo": shade(wa, -0.14, -0.08),
        "grille_line": shade(fl, -0.24, -0.06),
        "leg_hi": shade(ac, -0.06, -0.10), "leg_lo": shade(ac, -0.18, -0.08),
        "seam": shade(fl, -0.26, -0.04), "grain": shade(fl, -0.16, -0.06),
        "platter_hi": shade(ac, +0.20, -0.30), "platter_lo": shade(ac, +0.02, -0.26),
        "record_hi": shade(ac, -0.17, -0.20), "record_lo": shade(ac, -0.27, -0.18),
        "groove": shade(ac, -0.16, -0.20),
        "label": ac, "label_hi": shade(ac, +0.12, +0.04),
        "arm": shade(wa, +0.06), "arm_lo": shade(wa, -0.12),
        "armdark": shade(ac, -0.10, -0.10),
        "knob": shade(ac, +0.06), "knob_hi": shade(ac, +0.26, -0.06),
        "under": shade(fl, -0.30, -0.08),
    }


def grad_poly(base, pts, c_hi, c_lo, horiz=False):
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    x0, y0, x1, y1 = int(min(xs)), int(min(ys)), int(max(xs)) + 1, int(max(ys)) + 1
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    n = w if horiz else h
    ramp = Image.new("RGB", (w, h))
    dr = ImageDraw.Draw(ramp)
    for i in range(n):
        t = i / max(1, n - 1)
        c = tuple(round(a + (b - a) * t) for a, b in zip(c_hi, c_lo))
        dr.line([(i, 0), (i, h)] if horiz else [(0, i), (w, i)], fill=c)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon([(x - x0, y - y0) for x, y in pts], fill=255)
    base.paste(ramp, (x0, y0), mask)


def grad_ellipse(base, cx, cy, w, h, c_hi, c_lo):
    x0, y0 = int(cx - w / 2), int(cy - h / 2)
    bw, bh = int(w) + 1, int(h) + 1
    ramp = Image.new("RGB", (bw, bh))
    dr = ImageDraw.Draw(ramp)
    for i in range(bh):
        t = i / max(1, bh - 1)
        dr.line([(0, i), (bw, i)],
                fill=tuple(round(a + (b - a) * t) for a, b in zip(c_hi, c_lo)))
    mask = Image.new("L", (bw, bh), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, bw - 1, bh - 1], fill=255)
    base.paste(ramp, (x0, y0), mask)


def draw(p) -> Image.Image:
    im = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s = lambda v: v * SS
    ax, ay = ANCHOR[0] * SS, ANCHOR[1] * SS

    HW, BODY, LEG = s(250), s(124), s(232)
    hh = HW / 2
    bcy = ay - LEG                      # 몸통 밑면 마름모 중심
    b = [(ax - HW, bcy), (ax, bcy - hh), (ax + HW, bcy), (ax, bcy + hh)]
    t = [(x, y - BODY) for x, y in b]
    tcx, tcy = ax, bcy - BODY

    # --- 다리 넷 ------------------------------------------------------------
    # 위가 굵고 아래가 가늘며 밖으로 벌어진다. 높이는 안 기울인다 — 벌어짐은
    # 좌우로만 준다. 몸통보다 먼저 그려서 붙는 자리가 몸통에 가리게 한다.
    for corner in b:
        inx = (corner[0] - ax) * 0.80 + ax
        iny = (corner[1] - bcy) * 0.80 + bcy
        splay = (inx - ax) * 0.235
        wt, wb = s(15), s(7)
        grad_poly(im, [(inx - wt, iny), (inx + wt, iny),
                       (inx + splay + wb, iny + LEG), (inx + splay - wb, iny + LEG)],
                  p["leg_hi"], p["leg_lo"])

    # --- 뚜껑 ---------------------------------------------------------------
    # 뒤쪽(B-R) 모서리에서 경첩으로 열려 뒤로 젖혀진다. 보이는 면은 **안쪽**이라
    # 나뭇결이 보인다. 몸통보다 먼저 그린다 — 제일 뒤에 있다.
    B, R = t[1], t[2]
    lift = (-s(46), -s(232))
    B2 = (B[0] + lift[0], B[1] + lift[1])
    R2 = (R[0] + lift[0], R[1] + lift[1])
    grad_poly(im, [B, R, R2, B2], p["lid_hi"], p["lid_lo"])
    for i in range(1, 6):                      # 안쪽 나뭇결
        f = i / 6
        d.line([(B[0] + (R[0] - B[0]) * f, B[1] + (R[1] - B[1]) * f),
                (B2[0] + (R2[0] - B2[0]) * f, B2[1] + (R2[1] - B2[1]) * f)],
               fill=p["grain"], width=max(1, SS))
    d.line([B2, R2], fill=p["lid_edge"], width=int(s(5)))     # 뚜껑 두께
    d.line([B, B2], fill=p["seam"], width=SS)
    d.line([R, R2], fill=p["seam"], width=SS)

    # --- 몸통 ---------------------------------------------------------------
    grad_poly(im, [b[0], b[3], t[3], t[0]], p["wood_hi"], p["wood_lo"])
    grad_poly(im, [b[3], b[2], t[2], t[3]], p["side_hi"], p["side_lo"])

    # 앞면 스피커 그릴 — 사진의 천. 왼쪽(앞) 면에 짜임을 얹는다
    def inset(quad, k=0.86):
        cx = sum(q[0] for q in quad) / 4; cy = sum(q[1] for q in quad) / 4
        return [((x - cx) * k + cx, (y - cy) * k + cy) for x, y in quad]
    gq = inset([b[0], b[3], t[3], t[0]])
    grad_poly(im, gq, p["grille_hi"], p["grille_lo"])
    # 짜임은 **가로선만** 촘촘히. 격자로 그었더니 창살이 됐다
    for i in range(1, 14):
        f = i / 14
        d.line([(gq[0][0] + (gq[3][0] - gq[0][0]) * f, gq[0][1] + (gq[3][1] - gq[0][1]) * f),
                (gq[1][0] + (gq[2][0] - gq[1][0]) * f, gq[1][1] + (gq[2][1] - gq[1][1]) * f)],
               fill=p["grille_line"], width=max(1, SS // 2))

    for i in range(1, 4):                       # 오른쪽 면 나뭇결
        f = i / 4
        x0 = b[3][0] + (b[2][0] - b[3][0]) * f
        y0 = b[3][1] + (b[2][1] - b[3][1]) * f
        d.line([(x0, y0), (x0, y0 - BODY)], fill=p["grain"], width=max(1, SS))

    grad_poly(im, t, p["deck_hi"], p["deck_lo"])
    d.line(t + [t[0]], fill=p["seam"], width=SS)
    d.line([b[0], b[3], b[2]], fill=p["seam"], width=SS)
    for i in (0, 2, 3):
        d.line([b[i], t[i]], fill=p["seam"], width=SS)

    # --- 데크 위 -------------------------------------------------------------
    px, py = tcx - s(46), tcy + s(12)
    d.ellipse([px - s(120), py - s(56), px + s(120), py + s(68)], fill=p["under"])
    grad_ellipse(im, px, py, s(236), s(118), p["platter_hi"], p["platter_lo"])
    grad_ellipse(im, px, py, s(214), s(107), p["record_hi"], p["record_lo"])
    for r in (0.88, 0.76, 0.64):
        d.ellipse([px - s(107) * r, py - s(53) * r, px + s(107) * r, py + s(53) * r],
                  outline=p["groove"], width=max(1, SS))
    sheen = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(sheen).pieslice([px - s(107), py - s(53), px + s(107), py + s(53)],
                                   196, 250, fill=p["groove"] + (150,))
    im.alpha_composite(sheen)
    grad_ellipse(im, px, py, s(80), s(40), p["label_hi"], p["label"])
    d.ellipse([px - s(5), py - s(3), px + s(5), py + s(3)], fill=p["deck_hi"])

    piv = (tcx + s(150), tcy - s(20))
    tip = (px + s(84), py - s(12))
    d.ellipse([piv[0] - s(28), piv[1] - s(12), piv[0] + s(28), piv[1] + s(18)], fill=p["under"])
    grad_ellipse(im, piv[0], piv[1], s(52), s(26), p["knob_hi"], p["armdark"])
    d.line([piv, tip], fill=p["arm_lo"], width=int(s(10)))
    d.line([(piv[0], piv[1] - s(2)), (tip[0], tip[1] - s(2))], fill=p["arm"], width=int(s(5)))
    grad_ellipse(im, tip[0], tip[1], s(30), s(18), p["armdark"], p["record_lo"])

    for kx, ky in ((tcx + s(104), tcy + s(36)), (tcx + s(156), tcy + s(10))):
        d.ellipse([kx - s(19), ky - s(8), kx + s(19), ky + s(13)], fill=p["under"])
        grad_ellipse(im, kx, ky, s(36), s(18), p["knob_hi"], p["knob"])

    im = im.filter(ImageFilter.GaussianBlur(SS * 0.28))
    return im.resize((W, H), Image.LANCZOS)


sample = draw(palette(*THEMES[0][2:5]))
bb = sample.split()[3].getbbox()
x0, y0 = max(0, bb[0] - MARGIN), max(0, bb[1] - MARGIN)
x1, y1 = min(W, bb[2] + MARGIN), min(H, bb[3] + MARGIN)
cw, ch = x1 - x0, y1 - y0
box_w = TARGET_W
box_h = TARGET_W * ch / cw
anchor = ((ANCHOR[0] - x0) / cw * box_w, (ANCHOR[1] - y0) / ch * box_h)

for tid, fname, wall, floor, accent in THEMES:
    img = draw(palette(wall, floor, accent)).crop((x0, y0, x1, y1))
    dst = OUT / "themes" / fname
    dst.mkdir(parents=True, exist_ok=True)
    img.save(dst / "turntable.png")
    print(f"{tid:<16} -> themes/{fname}/turntable.png  {img.size}")

(OUT / "artbox.json").write_text(json.dumps({
    "w": round(box_w, 1), "h": round(box_h, 1),
    "ax": round(anchor[0] / box_w, 3), "ay": round(anchor[1] / box_h, 3),
    "cols": 2, "rows": 2,
}), encoding="utf-8")

print()
print(f"캔버스 {W}x{H}, 내용 {bb} -> 잘라서 {cw}x{ch}")
print("ItemBoxes 에 넣을 값:")
print(f"    ItemIds.TURNTABLE to box({box_w:.1f}f, {box_h:.1f}f, "
      f"{anchor[0]/box_w:.2f}f, {anchor[1]/box_h:.2f}f, 2, 2),")
