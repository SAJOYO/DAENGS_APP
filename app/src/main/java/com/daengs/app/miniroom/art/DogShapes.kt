package com.daengs.app.miniroom.art

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.daengs.app.R
import com.daengs.app.miniroom.RoomSpec

// ---------------------------------------------------------------------------
// 강아지 — **PNG 스프라이트 시트**
//
// 이 파일은 세 판본을 거쳤다. V1 파스텔 벡터, V2 도트(정수 격자에 칸 찍기),
// V3 잉크 만화. 셋 다 도형을 코드로 그렸고, 그래서 매번 화풍을 통째로 다시 짜야 했다.
//
// 이제 그리지 않는다. frankie516c/dog-training-rag 의 워크 시트를 그대로 쓴다.
// 남은 것은 **어느 시트를 쓸지 고르는 표** 하나뿐이다.
//
// 견종 축(털결·귀·꼬리)은 없앴다. 도형을 조립할 때나 필요하던 것이고, 시트를
// 쓰는 지금은 견종 = 시트 한 장이다. 저쪽에서 견종이 늘어나면 아래 표에 줄을
// 하나 추가하면 된다.
// ---------------------------------------------------------------------------

/**
 * 강아지 털색 한 벌.
 *
 * 시트가 이미 색까지 그려져 있어서 지금은 아무 데도 안 쓴다. 남겨둔 이유는
 * **사진에서 뽑은 색을 입히는 기능**이 CONTEXT.md 11번에 남아 있기 때문이다 —
 * 닮아 보이는 데 제일 크게 기여하는 게 털색이고, 털색은 모델이 필요 없다.
 * 그때 시트에 색조를 얹는 입구가 이 타입이 된다.
 */
@Immutable
data class DogCoat(
    val label: String,
    val body: Color,
) {
    companion object {
        val CREAM = DogCoat("크림", Color(0xFFF7E7CC))
        val APRICOT = DogCoat("애프리콧", Color(0xFFEFC098))
        val CHOCO = DogCoat("초코", Color(0xFFB5876A))
        val WHITE = DogCoat("화이트", Color(0xFFFFFCF8))

        val ALL: List<DogCoat> = listOf(CREAM, APRICOT, CHOCO, WHITE)
    }
}

/**
 * 견종 하나 = 워크 시트 한 장 + **그 견종만의 덩치·속도**.
 *
 * 시트는 전부 2328x568 짜리 가로 4프레임이라 규격이 같다. 그래서 견종을 늘리는 일이
 * **표에 한 줄 넣는 것**으로 끝난다 — 도형을 조립하던 시절에는 털결·귀·꼬리 축을
 * 일일이 골라야 했다.
 *
 * 규격이 같다고 **덩치까지 같은 건 아니다.** 처음 옮길 때 전 견종을 폭 13.5% 하나로
 * 통일했더니 치와와와 시베리안 허스키가 같은 크기로 섰다. 저쪽
 * (`ui-experiments/main-screen/drafts/dog-presets.js`) 은 견종마다 세 값을 따로
 * 잡아두고 있어서 그대로 가져왔다 — 보이는 크기, 바닥에서 차지하는 반경, 걷는 속도.
 *
 * 반경과 속도는 **칸 단위**라 격자 수가 다르면 뜻이 달라진다. 저쪽은 16 격자, 우리는
 * 12 격자다. 원본 값을 그대로 적어두고 읽을 때 환산하는 이유는, 저쪽 표가 갱신되면
 * 숫자를 그대로 덮어쓰면 되기 때문이다.
 *
 * [id] 가 그대로 카탈로그 키다 ([ItemCatalog] 가 `DogBreed.ALL` 로 자동 등록한다).
 */
@Immutable
enum class DogBreed(
    val id: String,
    val label: String,
    @DrawableRes val sheetRes: Int,
    /**
     * 얼굴 그림. 프로필 아바타처럼 방 밖에서 쓰는 자리용이다.
     *
     * 걷기 시트와는 화풍이 다르다 — 시트는 도트이고 이쪽은 사실풍 그림이다.
     * 저쪽이 그렇게 나눠 그렸고, 작게 띄우는 얼굴은 도트보다 이쪽이 알아보기 쉽다.
     *
     * 256x256 불투명이다. 동그럼게 자를 거면 부르는 쪽이 해야 한다.
     */
    @DrawableRes val portraitRes: Int,
    /**
     * 화면에 그릴 폭. **방 그림 폭 대비 %.**
     *
     * 시트 규격(582x568)은 견종이 같아도 그려진 덩치는 제각각이라, 같은 폭으로
     * 그리면 치와와와 허스키가 같은 크기가 된다. 저쪽이 견종마다 잡아둔 값이다.
     */
    val visualWidth: Float,
    /** 저쪽 `bodyRadius`. **저쪽 격자(16) 칸 단위** — 쓸 때는 [bodyRadius] 로 환산한다. */
    private val refBodyRadius: Float,
    /** 저쪽 `speed`. **저쪽 격자(16) 칸/초** — 쓸 때는 [speed] 로 환산한다. */
    private val refSpeed: Float,
) {

    BEAGLE(
        "dog_beagle", "비글", R.drawable.dog_beagle,
        portraitRes = R.drawable.dog_beagle_portrait,
        visualWidth = 13.0f, refBodyRadius = 0.58f, refSpeed = 0.54f,
    ),

    TOY_POODLE_SILVER(
        "dog_toy_poodle_silver", "실버 푸들", R.drawable.dog_toy_poodle_silver,
        portraitRes = R.drawable.dog_toy_poodle_silver_portrait,
        visualWidth = 12.0f, refBodyRadius = 0.5f, refSpeed = 0.5f,
    ),

    TOY_POODLE_LIGHT_BROWN(
        "dog_toy_poodle_light_brown", "연갈색 푸들", R.drawable.dog_toy_poodle_light_brown,
        portraitRes = R.drawable.dog_toy_poodle_light_brown_portrait,
        visualWidth = 12.0f, refBodyRadius = 0.5f, refSpeed = 0.5f,
    ),

    TOY_POODLE_CHOCOLATE(
        "dog_toy_poodle_chocolate", "초코 푸들", R.drawable.dog_toy_poodle_chocolate,
        portraitRes = R.drawable.dog_toy_poodle_chocolate_portrait,
        visualWidth = 12.0f, refBodyRadius = 0.5f, refSpeed = 0.5f,
    ),

    MALTESE(
        "dog_maltese", "말티즈", R.drawable.dog_maltese,
        portraitRes = R.drawable.dog_maltese_portrait,
        visualWidth = 11.0f, refBodyRadius = 0.46f, refSpeed = 0.53f,
    ),

    YORKSHIRE_TERRIER(
        "dog_yorkshire_terrier", "요크셔테리어", R.drawable.dog_yorkshire_terrier,
        portraitRes = R.drawable.dog_yorkshire_terrier_portrait,
        visualWidth = 10.5f, refBodyRadius = 0.45f, refSpeed = 0.57f,
    ),

    CHIHUAHUA(
        "dog_chihuahua", "치와와", R.drawable.dog_chihuahua,
        portraitRes = R.drawable.dog_chihuahua_portrait,
        visualWidth = 9.5f, refBodyRadius = 0.42f, refSpeed = 0.61f,
    ),

    BICHON_FRISE(
        "dog_bichon_frise", "비숑프리제", R.drawable.dog_bichon_frise,
        portraitRes = R.drawable.dog_bichon_frise_portrait,
        visualWidth = 11.5f, refBodyRadius = 0.48f, refSpeed = 0.52f,
    ),

    LABRADOR_RETRIEVER(
        "dog_labrador_retriever", "래브라도 리트리버", R.drawable.dog_labrador_retriever,
        portraitRes = R.drawable.dog_labrador_retriever_portrait_v2,
        visualWidth = 16.5f, refBodyRadius = 0.75f, refSpeed = 0.48f,
    ),

    /**
     * 걷기 시트를 `_puppy` 에서 무리 시트로 바꿨다.
     *
     * 퍼피 시트는 혼자 화풍이 달랐다 — 무리 25종은 머리가 크고 다리가 짧은
     * 치비인데 그것만 날씬하고 사실적이라, 한 방에 서면 다른 그림에서 오려온
     * 것처럼 보였다. 비율 문제라 필터로는 못 고치고 다시 그려야 한다.
     *
     * `dog_golden_retriever.webp` 는 원래 **래브라도 시트와 바이트까지 같았다**
     * (md5 43eaa39b). 그대로 쓰면 두 마리가 같은 개가 되므로 황금빛을 진하게
     * 밀어 갈라놓았다 — 시바·포메·푸들 변형을 만든 방법과 같다.
     */
    GOLDEN_RETRIEVER(
        "dog_golden_retriever", "골든리트리버", R.drawable.dog_golden_retriever,
        portraitRes = R.drawable.dog_golden_retriever_portrait_v2,
        visualWidth = 16.5f, refBodyRadius = 0.78f, refSpeed = 0.46f,
    ),

    /**
     * **저쪽 표에 없는 견종이다.** `dog-presets.js` 는 25종이고 스피츠와
     * 골든리트리버는 거기 없다 — 둘은 이 저장소에서 나중에 추가됐다. 그래서
     * 이 세 숫자는 옮겨온 값이 아니라 여기서 정한 값이고, 고쳐도 저쪽과
     * 어긋나지 않는다.
     *
     * 폭이 14.5 -> 16.0 -> 14.0 으로 두 번 움직였다. **시트를 바꿨기 때문이다.**
     * 옛 `_puppy` 시트는 개가 프레임에 작고 날씬하게 그려져 있어서 16.0 에서도
     * 화면에는 57x51px 로 나왔다 — 11.5% 인 흰 포메(58x57)보다도 작았다. 16.0 은
     * 그 시트를 보정하던 값이고, 프레임을 제대로 채우는 지금 시트에서는 과하다.
     * 14.0 이면 70x69px 로, 진돗개(68x73)보다 살짝 작고 웰시코기(77x57)와 덩치가
     * 비슷하며 흰 포메보다는 확실히 크다.
     *
     * 반경도 같이 내렸다(0.66 -> 0.60). 폭에 비례한 값보다 낮게 잡는다 — 스피츠는
     * 털이 부푼 견종이라 실루엣만큼 몸이 크지 않다. 진돗개가 14.5 에 0.64 인데
     * 그보다 작은 개가 더 큰 반경을 가질 이유가 없다.
     *
     * **0.66 이 위쪽 한계다.** [DogHerd.blockedAt] 이 몸의 네 모서리만 보기 때문에,
     * 우리 격자 반경이 0.5 를 넘으면 네 모서리가 1칸짜리 가구를 건너뛰어 그 위에
     * 서 있는데도 안 막힌 것으로 나온다. 0.68 로 올렸다가
     * `DogBlockingTest.가구 밑에 깔려도 걸어 나온다` 가 깨졌다. 0.60 은 0.45 다.
     *
     * 걷기 시트는 **흰 포메라니안 시트를 순백으로 리컬러한 것**이다. 원래 쓰던
     * `_puppy` 시트가 골든과 같은 이유로 혼자 화풍이 달랐는데, 무리 안에 흰
     * 스피츠형 개는 흰 포메 하나뿐이다 (말티즈·비숑은 귀가 처지고 털이 곱슬이라
     * 실루엣이 안 맞는다). 일본 스피츠는 포메보다 크고 더 새하얗다 — 크기는
     * visualWidth 16.0 대 11.5 가 갈라주고 색은 크림기를 뺐다.
     *
     * **진짜 스피츠 그림이 오면 이 파일만 갈아끼우면 된다.** 그러라고 포메를
     * 직접 가리키지 않고 제 이름의 리소스를 따로 뒀다.
     */
    JAPANESE_SPITZ(
        "dog_japanese_spitz", "스피츠", R.drawable.dog_japanese_spitz,
        portraitRes = R.drawable.dog_japanese_spitz_portrait,
        visualWidth = 14.0f, refBodyRadius = 0.60f, refSpeed = 0.54f,
    ),

    JINDO(
        "dog_jindo", "진돗개", R.drawable.dog_jindo,
        portraitRes = R.drawable.dog_jindo_portrait,
        visualWidth = 14.5f, refBodyRadius = 0.64f, refSpeed = 0.54f,
    ),

    SHIBA_INU_BLACK(
        "dog_shiba_inu_black", "검정 시바", R.drawable.dog_shiba_inu_black,
        portraitRes = R.drawable.dog_shiba_inu_black_portrait,
        visualWidth = 13.5f, refBodyRadius = 0.59f, refSpeed = 0.56f,
    ),

    SHIBA_INU_BEIGE(
        "dog_shiba_inu_beige", "베이지 시바", R.drawable.dog_shiba_inu_beige,
        portraitRes = R.drawable.dog_shiba_inu_beige_portrait,
        visualWidth = 13.5f, refBodyRadius = 0.59f, refSpeed = 0.56f,
    ),

    SHIBA_INU_ORANGE(
        "dog_shiba_inu_orange", "오렌지 시바", R.drawable.dog_shiba_inu_orange,
        portraitRes = R.drawable.dog_shiba_inu_orange_portrait,
        visualWidth = 13.5f, refBodyRadius = 0.59f, refSpeed = 0.56f,
    ),

    SIBERIAN_HUSKY(
        "dog_siberian_husky", "시베리안 허스키", R.drawable.dog_siberian_husky,
        portraitRes = R.drawable.dog_siberian_husky_portrait,
        visualWidth = 16.0f, refBodyRadius = 0.72f, refSpeed = 0.52f,
    ),

    POMERANIAN_BLACK_TAN(
        "dog_pomeranian_black_tan", "블랙탄 포메라니안", R.drawable.dog_pomeranian_black_tan,
        portraitRes = R.drawable.dog_pomeranian_black_tan_portrait,
        visualWidth = 11.5f, refBodyRadius = 0.46f, refSpeed = 0.57f,
    ),

    POMERANIAN_BEIGE(
        "dog_pomeranian_beige", "베이지 포메라니안", R.drawable.dog_pomeranian_beige,
        portraitRes = R.drawable.dog_pomeranian_beige_portrait,
        visualWidth = 11.5f, refBodyRadius = 0.46f, refSpeed = 0.57f,
    ),

    POMERANIAN_WHITE(
        "dog_pomeranian_white", "흰색 포메라니안", R.drawable.dog_pomeranian_white,
        portraitRes = R.drawable.dog_pomeranian_white_portrait,
        visualWidth = 11.5f, refBodyRadius = 0.46f, refSpeed = 0.57f,
    ),

    /**
     * **폭이 27종 중 가장 크지만 가장 큰 개는 아니다.** [visualWidth] 는 시트
     * 프레임의 폭이지 개의 폭이 아니다. 프레임 안에 개가 얼마나 크게 그려졌는지는
     * 견종마다 다른데, 보더콜리는 낮은 자세로 달리는 그림이라 프레임의 86x74%
     * 만 쓴다. 진돗개는 꼬리를 말아올려 83x93% 를 쓴다.
     *
     * 그래서 저쪽 값 15.5 로는 14.5 인 진돗개보다 **작아 보였다** — 발끝을 맞춰
     * 세우면 진돗개 73px, 보더콜리 64px 이었다. 래브라도(16.5, 75px)에 맞추려면
     * 18.5 가 필요하다. 17.0 이면 69px 로 아직 진돗개보다 작다.
     *
     * 반경은 0.68 그대로 뒀다. 보이는 크기와 따로 노는 값이고, 이미 0.5 를 넘어
     * [DogHerd.blockedAt] 의 네 모서리 검사가 성립하지 않는 견종이다 — 그걸
     * 고치기 전에 더 올리면 나빠지기만 한다.
     */
    BORDER_COLLIE(
        "dog_border_collie", "보더콜리", R.drawable.dog_border_collie,
        portraitRes = R.drawable.dog_border_collie_portrait,
        visualWidth = 18.5f, refBodyRadius = 0.68f, refSpeed = 0.59f,
    ),

    WELSH_CORGI(
        "dog_welsh_corgi", "웰시코기", R.drawable.dog_welsh_corgi,
        portraitRes = R.drawable.dog_welsh_corgi_portrait,
        visualWidth = 14.8f, refBodyRadius = 0.65f, refSpeed = 0.54f,
    ),

    DACHSHUND_SHORT_BROWN(
        "dog_dachshund_short_brown", "단모 갈색 닥스훈트", R.drawable.dog_dachshund_short_brown,
        portraitRes = R.drawable.dog_dachshund_short_brown_portrait,
        visualWidth = 16.5f, refBodyRadius = 0.67f, refSpeed = 0.52f,
    ),

    DACHSHUND_SHORT_BLACK(
        "dog_dachshund_short_black", "단모 검정 닥스훈트", R.drawable.dog_dachshund_short_black,
        portraitRes = R.drawable.dog_dachshund_short_black_portrait,
        visualWidth = 16.5f, refBodyRadius = 0.67f, refSpeed = 0.52f,
    ),

    DACHSHUND_LONG_BEIGE(
        "dog_dachshund_long_beige", "장모 베이지 닥스훈트", R.drawable.dog_dachshund_long_beige,
        portraitRes = R.drawable.dog_dachshund_long_beige_portrait,
        visualWidth = 17.0f, refBodyRadius = 0.69f, refSpeed = 0.5f,
    ),

    FRENCH_BULLDOG(
        "dog_french_bulldog", "프렌치불독", R.drawable.dog_french_bulldog,
        portraitRes = R.drawable.dog_french_bulldog_portrait,
        visualWidth = 12.0f, refBodyRadius = 0.55f, refSpeed = 0.47f,
    ),

    PUG(
        "dog_pug", "퍼그", R.drawable.dog_pug,
        portraitRes = R.drawable.dog_pug_portrait,
        visualWidth = 11.5f, refBodyRadius = 0.52f, refSpeed = 0.47f,
    ),

    SCHNAUZER(
        "dog_schnauzer", "슈나우저", R.drawable.dog_schnauzer,
        portraitRes = R.drawable.dog_schnauzer_portrait,
        visualWidth = 12.5f, refBodyRadius = 0.54f, refSpeed = 0.52f,
    );

    /**
     * 바닥에서 차지하는 반경(우리 격자 칸 단위).
     *
     * 보이는 크기와 **따로 논다.** 비숑처럼 털이 부푼 견종은 실루엣이 커도 실제로
     * 부딪히는 몸은 그만큼 크지 않다. 저쪽이 두 값을 나눠 둔 이유다.
     *
     * 발끝 한 점만 검사하면 몸통이 가구에 반쯤 파묻힌 채 멈춘다. 그렇다고 크게
     * 잡으면 가구 사이를 못 지나간다 — 통로가 이 값의 두 배보다 넓어야 한다.
     */
    val bodyRadius: Float get() = refBodyRadius * GRID_RATIO

    /** 걷는 속도(우리 격자 칸/초). 치와와는 총총, 래브라도는 느긋하다. */
    val speed: Float get() = refSpeed * GRID_RATIO

    companion object {
        /**
         * 저쪽 격자 수. 칸 단위 값을 우리 격자로 옮길 때 쓴다.
         *
         * 우리 칸이 1.33 배 크므로 같은 몸집·같은 화면 속도가 되려면
         * **칸 단위 값은 그만큼 작아야** 한다.
         */
        private const val REF_GRID = 16f

        private val GRID_RATIO = RoomSpec.GRID / REF_GRID

        val ALL: List<DogBreed> = entries

        /**
         * 등록한 강아지가 없을 때 방을 채우는 품종.
         *
         * 이제 방에는 **사용자가 등록한 견종**이 선다 (27종 다 걷기 시트가 있다).
         * 이 셋은 아직 목록을 못 받아 온 자리와, 그림이 없는 견종의 대역으로 쓴다.
         */
        val ROOM_BREEDS: List<DogBreed> = listOf(
            JAPANESE_SPITZ,
            LABRADOR_RETRIEVER,
            GOLDEN_RETRIEVER,
        )

        /** 명부를 아직 못 받아 온 자리를 채우는 데모 명부. */
        fun demoRoster(count: Int): List<DogBreed> =
            List(count) { ROOM_BREEDS[it % ROOM_BREEDS.size] }

        /**
         * 그림이 없는 견종(믹스 등)이 방에서 대신 설 종.
         *
         * **얼굴처럼 발자국으로 떨어뜨릴 수 없다.** 방 강아지는 걷고 앉는 전신 시트라
         * 대체할 중립 그림이 없고, 안 세우면 자기 강아지가 방에서 사라진다.
         *
         * [key] 로 정한다 — 강아지 id 를 넣으면 **같은 아이는 언제나 같은 대역**이라
         * 앱을 켤 때마다 종이 바뀌지 않는다.
         *
         * 믹스 시트가 오면 **이 함수만** 바꾸면 된다.
         */
        fun roomStandIn(key: String): DogBreed =
            ROOM_BREEDS[((key.hashCode() % ROOM_BREEDS.size) + ROOM_BREEDS.size) % ROOM_BREEDS.size]

        private val index = entries.associateBy { it.id }

        /** 아트 키 → 견종. 렌더러가 `DogActor.breed.id` 로 되찾을 때 쓴다. */
        fun byId(id: String): DogBreed? = index[id]
    }
}

/**
 * 강아지 한 마리의 **지금 자세**. 리깅에 넘기는 유일한 입력이다.
 *
 * 시트를 쓰든 도형을 그리든 이 값만 받으면 되므로, 아트를 갈아끼워도
 * 움직이는 코드는 그대로다.
 */
@Immutable
data class DogPose(
    /** 걸음 위상(라디안). 이동 거리에 비례해 늘어나므로 속도와 자동으로 맞는다. */
    val phase: Float = 0f,
    /**
     * 0 = 서 있음, 1 = 걷는 중.
     *
     * **불리언이 아니라 실수인 이유**: 걷기 시작·멈춤이 툭 끊기면 몸이 순간이동한다.
     * [com.daengs.app.miniroom.DogHerd] 가 목표값으로 부드럽게 몰아준다.
     */
    val stand: Float = 0f,
) {
    companion object {
        /** 가만히 있는 자세. 미리보기·인벤토리 아이콘이 쓴다. */
        val Idle = DogPose()
    }
}

/**
 * 시트를 못 읽었을 때의 폴백.
 *
 * 리소스가 정상이면 절대 안 불린다. 불렸다는 건 시트가 빠졌다는 뜻이므로
 * **눈에 띄는 분홍 상자**를 그린다 — 조용히 아무것도 안 그리면 원인을 못 찾는다.
 * ([ItemCatalog] 의 무드등 폴백과 같은 방식이다.)
 */
fun DrawScope.drawDogBreed(
    breed: DogBreed,
    frame: Int,
    pose: DogPose = DogPose.Idle,
) {
    drawRect(com.daengs.app.ui.theme.RoomPalette.GhostInvalid, Offset.Zero, Size(151.5f, 147.8f))
}
