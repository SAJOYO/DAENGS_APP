package com.daengs.app.miniroom

import com.daengs.app.miniroom.art.DogBreed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 견종마다 **고유한 덩치**를 갖는가.
 *
 * 저쪽 에셋을 옮길 때 시트 규격(582x568)이 전 견종 같다는 이유로 화면 크기까지
 * 하나(13.5%)로 통일한 적이 있다. 그러면 치와와와 시베리안 허스키가 같은 크기로
 * 서고, 방 안에 여러 견종이 있다는 게 눈에 안 들어온다.
 *
 * 값의 출처는 저쪽 `ui-experiments/main-screen/drafts/dog-presets.js` 다.
 * 그 표가 갱신되면 여기 숫자도 같이 고친다.
 */
class DogBreedPresetTest {

    /** 12 격자 / 16 격자. 칸 단위 값은 이만큼 작아진다. */
    private val ratio = RoomSpec.GRID / 16f

    @Test
    fun `견종마다 크기가 다르다`() {
        val widths = DogBreed.ALL.map { it.visualWidth }.toSet()
        assertTrue("전 견종이 같은 폭이면 견종 구분이 안 된다", widths.size > 1)
        assertEquals("가장 작은 견종은 치와와", 9.5f, DogBreed.ALL.minOf { it.visualWidth }, 0.001f)
        // 폭이 가장 큰 것은 보더콜리다. 개가 제일 커서가 아니라 **프레임 안에
        // 작게 그려져서** 그렇다 — 낮게 달리는 자세라 프레임의 86x74% 만 쓴다.
        // visualWidth 는 프레임의 폭이므로 그만큼 키워야 다른 개와 같아 보인다.
        assertEquals(
            "폭이 가장 큰 견종은 보더콜리",
            18.5f,
            DogBreed.ALL.maxOf { it.visualWidth },
            0.001f,
        )
    }

    /**
     * 크기 차이가 눈에 보일 만큼은 되는가. 1.5 배는 방 안에서 확실히 구분되는 폭이다.
     * 값이 조금씩만 다르면 "고쳤다"고 착각하면서 사실상 통일된 것과 같아진다.
     */
    @Test
    fun `가장 큰 견종은 가장 작은 견종의 한배 반이 넘는다`() {
        val small = DogBreed.ALL.minOf { it.visualWidth }
        val big = DogBreed.ALL.maxOf { it.visualWidth }
        assertTrue("$big / $small 배밖에 안 된다", big / small > 1.5f)
    }

    /** 칸 단위 값은 우리 격자로 환산돼야 한다. 안 하면 저쪽 격자 기준이라 1.33 배 커진다. */
    @Test
    fun `반경과 속도는 우리 격자로 환산된다`() {
        assertEquals(0.42f * ratio, DogBreed.CHIHUAHUA.bodyRadius, 0.0001f)
        assertEquals(0.61f * ratio, DogBreed.CHIHUAHUA.speed, 0.0001f)
        assertEquals(0.75f * ratio, DogBreed.LABRADOR_RETRIEVER.bodyRadius, 0.0001f)
        assertEquals(0.48f * ratio, DogBreed.LABRADOR_RETRIEVER.speed, 0.0001f)
        assertEquals(0.69f * ratio, DogBreed.DACHSHUND_LONG_BEIGE.bodyRadius, 0.0001f)
    }

    /**
     * 몸 반경은 보이는 크기와 **따로 논다.**
     *
     * 털이 부푼 견종(비숑·포메)은 실루엣이 커도 부딪히는 몸은 그만큼 크지 않다.
     * 저쪽이 두 값을 나눠 둔 이유이고, 한쪽에서 다른 쪽을 계산해내면 안 된다.
     */
    @Test
    fun `털이 부푼 견종은 덩치보다 반경이 작다`() {
        // 포메(11.5%)와 퍼그(11.5%)는 폭이 같지만 반경이 다르다
        assertEquals(DogBreed.POMERANIAN_BEIGE.visualWidth, DogBreed.PUG.visualWidth, 0.001f)
        assertTrue(
            "폭이 같아도 반경까지 같으면 폭에서 반경을 계산하고 있는 것이다",
            DogBreed.POMERANIAN_BEIGE.bodyRadius < DogBreed.PUG.bodyRadius,
        )
    }

    /**
     * 색만 다른 변형은 **덩치가 같아야 한다.**
     *
     * 시바 세 색·포메 세 색·푸들 세 색은 같은 그림을 리컬러한 것이라 실루엣이 같다.
     * 하나만 값이 어긋나면 같은 견종인데 크기가 달라 보인다.
     */
    @Test
    fun `색 변형끼리는 덩치가 같다`() {
        listOf(
            "시바" to listOf(DogBreed.SHIBA_INU_BLACK, DogBreed.SHIBA_INU_BEIGE, DogBreed.SHIBA_INU_ORANGE),
            "포메" to listOf(DogBreed.POMERANIAN_BLACK_TAN, DogBreed.POMERANIAN_BEIGE, DogBreed.POMERANIAN_WHITE),
            "푸들" to listOf(
                DogBreed.TOY_POODLE_SILVER,
                DogBreed.TOY_POODLE_LIGHT_BROWN,
                DogBreed.TOY_POODLE_CHOCOLATE,
            ),
        ).forEach { (name, group) ->
            assertEquals("$name 폭", 1, group.map { it.visualWidth }.distinct().size)
            assertEquals("$name 반경", 1, group.map { it.bodyRadius }.distinct().size)
            assertEquals("$name 속도", 1, group.map { it.speed }.distinct().size)
        }
    }

    /**
     * 가장 큰 반경이 [DogHerd.MIN_DOG_GAP] 안에 들어오는가.
     *
     * 목적지 간격은 견종과 무관하게 하나다. 가장 큰 개 둘이 서로의 목적지에 서도
     * 겹치지 않아야 그 간격이 뜻을 갖는다.
     */
    @Test
    fun `가장 큰 개 둘이 목적지 간격 안에서 안 겹친다`() {
        val biggest = DogBreed.ALL.maxOf { it.bodyRadius }
        assertTrue(
            "반경 $biggest 둘이면 ${biggest * 2} 인데 간격이 ${DogHerd.MIN_DOG_GAP} 다",
            biggest * 2f < DogHerd.MIN_DOG_GAP,
        )
    }

    /**
     * 얼굴 그림이 견종마다 따로 붙었는가.
     *
     * 25개 항목에 한 줄씩 손으로 넣은 값이라, 복사하다 한 장을 둘이 나눠 쓰기 쉽다.
     * 그래도 컴파일은 멀쩡히 되고 엉뚱한 개 얼굴이 뜰 뿐이라 눈으로는 놓친다.
     */
    @Test
    fun `얼굴 그림이 견종마다 다르다`() {
        assertEquals(
            "얼굴 그림이 겹친다",
            DogBreed.ALL.size,
            DogBreed.ALL.map { it.portraitRes }.distinct().size,
        )
        assertTrue("얼굴 그림이 비었다", DogBreed.ALL.all { it.portraitRes != 0 })
        assertTrue(
            "얼굴 그림이 걷기 시트와 같다",
            DogBreed.ALL.all { it.portraitRes != it.sheetRes },
        )
    }
}
