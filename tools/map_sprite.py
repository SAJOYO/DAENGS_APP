"""지도 전봇대 반입 규격: 256×640, 바닥 접점 (128, 624).

내장 생성 도구가 알파 대신 밝은 체크무늬/흰 매트를 반환한 경우 바깥과
이어진 밝은 영역만 제거한다. 내부의 흰 포스터/애자는 보존한다.
입력은 어두운 외곽선, 배경 없는 전봇대 한 장이어야 한다.
"""
from pathlib import Path
from PIL import Image
import room_cutout

SIZE = (256, 640)
FOOT = (128, 624)


def prepare_map_sprite(source: Path) -> Image.Image:
    image = Image.open(source).convert("RGBA")
    if image.getchannel("A").getextrema()[0] == 255:
        pixels = image.load()
        # 밝은 생성 매트를 제거하되 어두운 외곽선에서 멈춘다.
        tolerance = room_cutout.TOLERANCE
        try:
            room_cutout.TOLERANCE = 65
            outside = room_cutout.flood_from_border(pixels, *image.size, (255, 255, 255))
        finally:
            room_cutout.TOLERANCE = tolerance
        for y in range(image.height):
            for x in range(image.width):
                if outside[y * image.width + x]:
                    pixels[x, y] = (0, 0, 0, 0)
        if sum(outside) < image.width * image.height * .1:
            raise ValueError("지도 마커 바깥 배경을 분리할 수 없습니다. 알파 입력을 사용하세요.")
    # 생성 알파의 거의 투명한 먼지로 크기·밑면이 달라지지 않게 한다.
    # 실제 픽셀의 알파(외곽 안티앨리어싱)는 그대로 보존한다.
    silhouette = image.getchannel("A").point(lambda alpha: 255 if alpha > 16 else 0)
    bounds = silhouette.getbbox()
    if bounds is None:
        raise ValueError("빈 지도 마커입니다.")
    left, top, right, bottom = bounds
    # 기울어진 가로대가 좌우 중심을 바꿔도 기둥 밑면 중심은 같은 좌표에 둔다.
    foot_bounds = silhouette.crop((0, max(top, bottom - 12), image.width, bottom)).getbbox()
    foot_x = (foot_bounds[0] + foot_bounds[2]) / 2
    content = image.crop(bounds)
    scale = min(224 / content.width, 600 / content.height)
    width, height = round(content.width * scale), round(content.height * scale)
    content = content.resize((width, height), Image.Resampling.LANCZOS)
    target = Image.new("RGBA", SIZE)
    x = round(FOOT[0] - (foot_x - left) * width / (right - left))
    y = FOOT[1] - height
    target.alpha_composite(content, (x, y))
    return target
