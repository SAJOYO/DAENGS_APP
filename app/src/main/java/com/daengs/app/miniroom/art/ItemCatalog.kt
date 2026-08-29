package com.daengs.app.miniroom.art

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import com.daengs.app.R
import com.daengs.app.miniroom.ItemIds
import com.daengs.app.miniroom.RoomDefaults
import com.daengs.app.miniroom.RoomSpec
import com.daengs.app.miniroom.RoomTheme
import com.daengs.app.miniroom.sprite.SpriteSheet
import com.daengs.app.ui.theme.RoomPalette

@Immutable
class ItemCatalog(private val map: Map<String, ItemArt>) {
    operator fun get(itemId: String): ItemArt? = map[itemId]
    val ids: Set<String> get() = map.keys
}

/**
 * 아이템 카탈로그.
 *
 * **PNG 로 교체하는 법**: 아래에서 해당 줄의 [ItemArtSpec.Shapes] 를
 * [ItemArtSpec.Res] 로 바꾸고 resId 를 넣기만 하면 된다. ArtBox 만 그대로면
 * 정렬·터치판정·스냅·점유 계산은 한 줄도 안 바뀐다.
 *
 * ```
 * "plant" to ItemArtSpec.Shapes(PlantBox) { drawPlant() }
 * // →
 * "plant" to ItemArtSpec.Res(PlantBox, resId = R.drawable.plant_01)
 * ```
 *
 * 강아지는 이미 [ItemArtSpec.Sheet] 라서 `resId = 0` 을 진짜 시트로만 바꾸면 된다.
 */
/**
 * 소품 규격 — **테마와 무관한 부분**이다.
 *
 * 저쪽 테마 팩은 크기와 실루엣을 원본 그대로 유지한 채 색만 바꾼다. 그래서 상자·
 * 기준점·발자국은 테마가 바뀌어도 같고, 바뀌는 건 그림 하나뿐이다.
 *
 * 값은 저쪽 목업에서 환산했다. 저쪽은 `width` 를 **스테이지 폭의 %** 로 적고 높이를
 * `aspectRatio` 로 유도하는데, 우리 기본 단위가 방 PNG 픽셀이라 곧장 옮겨진다.
 *   size.width  = width% x 1122
 *   size.height = size.width / aspectRatio
 *   anchor      = artAnchor% x size
 * footprint 는 저쪽 16 격자 기준이라 우리 12 에 맞춰 x0.75 했다.
 *
 * [RoomDefaults.withFixtures] 가 붙박이 칸을 재는 데도 쓴다 — 그래서 public 이다.
 * 리소스 해석이 필요 없는 순수 데이터라 단위 테스트에서 그대로 읽을 수 있다.
 */
val ItemBoxes: Map<String, ArtBox> = mapOf(
    ItemIds.RUG to box(437.6f, 235.5f, 0.50f, 0.50f, 5, 5, flat = true),
    ItemIds.RUG_CREAM to box(437.6f, 234.9f, 0.50f, 0.50f, 5, 5, flat = true),
    ItemIds.PLANT to box(101.0f, 162.8f, 0.50f, 0.93f, 1, 2),
    ItemIds.DOGHOUSE to box(218.8f, 224.7f, 0.50f, 0.78f, 2, 3),
    ItemIds.BALL to box(53.9f, 55.5f, 0.50f, 0.94f, 1, 1),
    ItemIds.CABINET to box(246.8f, 242.4f, 0.50f, 0.77f, 4, 2),
    ItemIds.BASKET to box(101.0f, 87.0f, 0.50f, 0.78f, 2, 2),
    ItemIds.BOWLS to box(101.0f, 51.6f, 0.50f, 0.58f, 2, 1),
    // 뚜껑을 연 콘솔이라 세로가 길다. 기준점이 0.87 로 낮은 것은 **바닥에 닿는 점이
    // 다리 끝**이기 때문이다 — 몸통 밑면이 아니다.
    ItemIds.TURNTABLE to box(200.0f, 316.7f, 0.50f, 0.87f, 2, 2),
)

/**
 * @param anchorX 그림 안에서 **바닥에 닿는 점**의 비율(0..1). 세워두는 물건은
 *   아래쪽(0.8 언저리), 바닥에 눕는 러그는 한가운데(0.5)다.
 */
private fun box(
    w: Float,
    h: Float,
    anchorX: Float,
    anchorY: Float,
    cols: Int,
    rows: Int,
    flat: Boolean = false,
) = ArtBox(
    footprint = IntSize(cols, rows),
    size = Size(w, h),
    anchor = Offset(w * anchorX, h * anchorY),
    flat = flat,
)

/** 이 테마의 소품 + 견종 전체. */
fun itemSpecs(theme: RoomTheme): Map<String, ItemArtSpec> =
    ItemBoxes.mapValues { (id, box) ->
        ItemArtSpec.Res(
            box = box,
            // 붙박이는 강아지와 같은 길로 막는다 — pickTopmost 가 안 잡으면
            // 드래그·선택·돌리기/치우기 버튼이 한꺼번에 없어진다.
            movable = id !in RoomDefaults.FIXTURE_IDS,
            resId = theme.art(id),
            // 픽셀 아트라 보간을 끈다. 기본값(Medium)이면 확대할 때 뿌옇게 번진다.
            filterQuality = FilterQuality.None,
        )
    } + DogBreed.ALL.associate { it.id to dogSpec(it) }

/**
 * 강아지. 저쪽 워크 시트를 그대로 쓴다.
 *
 * 시트는 2328x568 짜리 가로 4칸이라 한 프레임이 582x568 이다. 규격은 같지만
 * **덩치는 견종마다 다르다** — 폭은 [DogBreed.visualWidth] 가 정한다. 한때 전부
 * 13.5% 로 통일했다가 치와와와 허스키가 같은 크기로 서는 꼴을 봤다.
 *
 * 높이는 시트 비율(568/582)로 따라간다. 프레임을 통째로 그리는 것이라 여기서
 * 세로를 따로 정하면 그림이 늘어난다.
 */
private fun dogSpec(breed: DogBreed): ItemArtSpec.Sheet {
    val w = breed.visualWidth / 100f * RoomSpec.ROOM_PNG_W
    val h = w * DOG_FRAME_H / DOG_FRAME_W
    return ItemArtSpec.Sheet(
    box = ArtBox(
        size = Size(w, h),
        anchor = Offset(w * 0.5f, h * 0.94f),
    ),
    movable = false,
    resId = breed.sheetRes,
    frameWidth = DOG_FRAME_W.toInt(),
    frameHeight = DOG_FRAME_H.toInt(),
    columns = 4,
    frameCount = 4,
    fps = 5,
    filterQuality = FilterQuality.None,
    ) { frame -> drawDogBreed(breed, frame) }
}

/** 워크 시트 한 프레임의 원본 크기. 전 견종 공통이다. */
private const val DOG_FRAME_W = 582f
private const val DOG_FRAME_H = 568f

/**
 * 사람이 읽는 이름. 인벤토리 목록과 개발자 패널이 쓴다.
 *
 * **[ItemIds.ALL] 에 id 를 더하면 여기도 한 줄 더한다.** 빠지면 조용히 id 가
 * 그대로 화면에 뜬다 (`ItemLabels[id] ?: id`). #15 가 그래서 `turntable` 로 떴다.
 * `ItemLabelsTest` 가 이제 그 자리를 잡는다.
 */
val ItemLabels: Map<String, String> = mapOf(
    ItemIds.RUG to "러그",
    ItemIds.RUG_CREAM to "크림 러그",
    ItemIds.PLANT to "큰 화분",
    ItemIds.DOGHOUSE to "강아지 집",
    ItemIds.BALL to "공",
    ItemIds.CABINET to "수납장",
    ItemIds.BASKET to "장난감 바구니",
    ItemIds.BOWLS to "밥그릇 세트",
    ItemIds.TURNTABLE to "턴테이블",
) + DogBreed.ALL.associate { it.id to it.label }

/**
 * 선언(spec)을 그리기 가능한 형태(art)로 해석한다.
 * 리소스 해석이 @Composable 이라 이 단계만 합성 안에 있고, 그리기 단계는 순수하다.
 */
@Composable
fun rememberItemCatalog(theme: RoomTheme = RoomTheme.DEFAULT): ItemCatalog {
    val specs = itemSpecs(theme)
    val resolved = LinkedHashMap<String, ItemArt>(specs.size)
    for ((id, spec) in specs) {
        resolved[id] = when (spec) {
            is ItemArtSpec.Shapes ->
                ItemArt.Shapes(spec.box, spec.movable, spec.draw)

            is ItemArtSpec.Res ->
                if (spec.resId != 0) {
                    ItemArt.Bitmap(
                        spec.box,
                        spec.movable,
                        ImageBitmap.imageResource(spec.resId),
                        spec.filterQuality,
                    )
                } else {
                    ItemArt.Shapes(spec.box, spec.movable) { }
                }

            is ItemArtSpec.Sheet ->
                ItemArt.Sheet(
                    box = spec.box,
                    movable = spec.movable,
                    sheet = if (spec.resId != 0) {
                        SpriteSheet(
                            image = ImageBitmap.imageResource(spec.resId),
                            frameWidth = spec.frameWidth,
                            frameHeight = spec.frameHeight,
                            columns = spec.columns,
                            frameCount = spec.frameCount,
                            fps = spec.fps,
                            filterQuality = spec.filterQuality,
                        )
                    } else {
                        null
                    },
                    frameCount = spec.frameCount,
                    fps = spec.fps,
                    fallback = spec.fallback,
                )
        }
    }
    // 테마가 바뀌면 그림이 통째로 달라지므로 테마 id 를 키로 잡는다.
    return remember(theme.id) { ItemCatalog(resolved) }
}
