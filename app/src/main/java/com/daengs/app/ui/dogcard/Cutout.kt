package com.daengs.app.ui.dogcard


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---------------------------------------------------------------------------
// 누끼 — 사진에서 강아지만 오려낸다
//
// **API 를 쓰지 않는다.** 키도 서버도 과금도 없고 추론이 전부 폰 안에서 돈다.
// 다만 모델이 APK 에 없다 — Play 서비스가 그 기기 첫 사용 때 한 번 내려받는다.
// 그래서 "없을 수 있는 것"으로 다루고, 없으면 [Cutout.Result.Ellipse] 로 물러선다.
//
// ## 왜 폴백이 선택이 아니라 필수인가
//
// Play 서비스가 없는 기기, 모델을 못 받은 기기, 세그멘테이션이 실패하는 사진이
// 전부 있다. **카드가 안 나오면 안 된다.** 이 저장소가 늘 하는 방식이다 —
// `AssetImage` 는 null 을 돌려주고, `SceneMusic` 은 조용히 무음이 되고,
// `drawDogBreed` 는 시트를 못 읽으면 분홍 상자를 그린다.
//
// ## 가장자리를 블러로 흐리지 않는다
//
// `RenderEffect`(블러)는 API 31 부터인데 minSdk 26 이다 — `ImmersiveScreen` 이
// 이미 같은 벽에 부딪혀 여러 장 겹치기로 우회했다. 여기서는 `tools/room_cutout.py`
// 의 `add_outline()` 을 옮긴다: **알파를 부풀려 띠를 두르고 "오린 티"를 그 안에
// 먹인다.** 스티커 테두리라 트레이딩 카드에는 오히려 어울린다.
// ---------------------------------------------------------------------------

object Cutout {

    /** 누끼 결과의 긴 변. 카드 그림창이 810px 언저리라 이보다 클 필요가 없다. */
    const val MAX_EDGE = 900

    /** 이 값 이상이면 "칠해진 픽셀"로 본다. `room_cutout.py` 의 `ALPHA_FLOOR` 와 같은 뜻. */
    private const val ALPHA_FLOOR = 8

    /** 테두리 띠의 두께(결과 긴 변 대비). 900px 이면 9px 쯤 된다. */
    private const val OUTLINE_RATIO = 0.010f

    /**
     * 모델이 준비되기를 기다리는 한도(ms).
     *
     * **기다리지 않으면 그 기기의 첫 호출은 반드시 실패한다.** 실측했다 —
     * 처음 누르면 `Waiting for the subject segmentation optional module to be
     * downloaded` 가 나오고 조용히 타원으로 물러섰다. 사용자의 첫 카드가 가장
     * 나쁜 결과를 받는 셈이라 그냥 둘 수 없다.
     *
     * 한도를 두는 이유는 Play 서비스가 없는 기기에서 영영 안 끝나기 때문이다.
     */
    private const val MODEL_WAIT_MS = 20_000L

    /** 원형 마스크의 가장자리를 흐리는 폭(반지름 대비). 딱 자르면 톱니가 보인다. */
    private const val CIRCLE_FEATHER = 0.06f

    /** [Mask.SoftBottom] 이 아래에서부터 녹이는 높이(결과 높이 대비). */
    private const val FADE_HEIGHT = 0.22f

    /**
     * 오려낸 것을 어떤 모양으로 남길 것인가.
     *
     * 세그멘테이션은 **상자 안에서 배경만** 지운다. 상자에 목과 가슴이 들어와 있으면
     * 그것도 피사체라 같이 남는다 — 얼굴만 쓰고 싶을 때 이게 문제가 된다.
     */
    enum class Mask {
        /** 누끼 알파 그대로. 귀 실루엣이 살지만 **목이 남는다.** */
        Silhouette,

        /** 원으로 잘라낸다. 목이 깔끔히 빠지지만 **처진 귀가 잘린다.** */
        Circle,

        /**
         * 누끼를 두되 아래쪽만 서서히 지운다.
         *
         * 귀는 살고 목은 녹아 없어진다. 초상화에서 흔한 방식이고, 자른 자리가
         * 직선으로 남지 않아 "오려 붙인 사진" 으로 안 보인다.
         */
        SoftBottom,
    }

    sealed interface Result {
        val bitmap: Bitmap

        /** 세그멘테이션이 실제로 오려냈다. */
        data class Cut(override val bitmap: Bitmap) : Result

        /**
         * 오려내지 못해 상자를 타원으로 잘랐다.
         *
         * [why] 는 화면에 그대로 보여 줄 것이 아니라 **왜 물러섰는지 남기는 것**이다.
         * 모델을 내려받는 중인 것과 이 기기에서 영영 안 되는 것은 다른 일인데,
         * 둘 다 조용히 타원이 되면 구분할 방법이 없다.
         */
        data class Ellipse(
            override val bitmap: Bitmap,
            val why: String,
            /**
             * 모델이 아직 준비되지 않았을 뿐인가.
             *
             * true 면 **다시 눌러 볼 만하다.** 이 기기에서 영영 안 되는 것과
             * 구분해야 하는 이유가 그것이다 — 안내 문장이 정반대가 된다.
             */
            val pending: Boolean = false,
        ) : Result
    }

    /**
     * 모델을 미리 내려받아 둔다. 실패해도 아무 일도 일어나지 않는다.
     *
     * 사진을 고르고 얼굴 상자를 맞추는 동안 이게 돌아가면, 정작 누를 때는 이미
     * 준비돼 있다. [of] 안에서도 기다리지만 그때 기다리면 사용자가 기다린다.
     */
    suspend fun warmUp() {
        withContext(Dispatchers.Default) {
            runCatching {
                val segmenter = SubjectSegmentation.getClient(
                    SubjectSegmenterOptions.Builder().enableForegroundBitmap().build(),
                )
                try {
                    withTimeoutOrNull(MODEL_WAIT_MS) { segmenter.initTask.await() }
                } finally {
                    segmenter.close()
                }
            }
        }
    }

    private val Throwable?.isPending: Boolean
        get() = this is MlKitException && errorCode == MlKitException.UNAVAILABLE

    private fun reasonOf(cause: Throwable?): String = when {
        cause.isPending -> "모델을 아직 내려받는 중입니다. 잠시 후 다시 해 보세요."
        cause == null -> "피사체를 찾지 못했습니다."
        else -> cause.message ?: "피사체를 찾지 못했습니다."
    }

    /**
     * [photo] 에서 강아지를 오려낸다. **[photo] 는 지우지 않는다** — 부르는 쪽 것이다.
     *
     * @param box 얼굴 자리. 정규화 `[x, y, w, h]` — `GuideFrameScreen` 이 주는 것과
     *   같은 모양이다. null 이면 사진 전체에서 찾는다.
     *
     *   **상자를 먼저 받고 그 안에서 오리는 쪽이 안정적이다.** 사진 한 장에 강아지가
     *   둘이거나 사람이 같이 찍혀 있으면 "피사체"가 우리가 원하는 것이 아닐 수 있고,
     *   얼굴만 쓸 것이라면 어차피 잘라야 한다.
     */
    suspend fun of(photo: Bitmap, box: FloatArray? = null, mask: Mask = Mask.Silhouette): Result =
        withContext(Dispatchers.Default) {
            val cropped = box?.let { photo.crop(it) } ?: photo
            val scaled = cropped.scaledToFit(MAX_EDGE)
            if (cropped !== photo && cropped !== scaled) cropped.recycle()

            val attempt = runCatching { segment(scaled) }
            val foreground = attempt.getOrNull()

            if (foreground == null) {
                // 물러설 때는 **자른 것을** 타원으로 만든다. 원본 전체가 아니다.
                val masked = scaled.ellipseMasked()
                if (scaled !== photo) scaled.recycle()
                val cause = attempt.exceptionOrNull()
                // 폴백은 이미 타원이라 [Mask] 를 또 씌우지 않는다.
                Result.Ellipse(withOutline(masked), reasonOf(cause), pending = cause.isPending)
            } else {
                if (scaled !== photo && scaled !== foreground) scaled.recycle()
                // **원형은 테두리를 두르기 전에** 씌운다. 그래야 띠가 원을 따라 돌아
                // 아바타처럼 보인다. 아래 페이드는 반대로 **두른 뒤**다 — 먼저 하면
                // 옅어진 알파를 따라 띠가 호를 그린다.
                val shaped = if (mask == Mask.Circle) foreground.circleMasked() else foreground
                if (shaped !== foreground) foreground.recycle()
                val trimmed = shaped.trimToContent()
                if (trimmed !== shaped) shaped.recycle()
                val outlined = withOutline(trimmed)
                Result.Cut(if (mask == Mask.SoftBottom) outlined.fadedBottom() else outlined)
            }
        }

    // -- 세그멘테이션 --------------------------------------------------------

    /**
     * 알파가 들어간 피사체 비트맵을 받아 온다.
     *
     * `enableForegroundBitmap()` 이면 결과에서 바로 [Bitmap] 이 나온다 — 신뢰도
     * 마스크(`FloatBuffer`)를 받아 우리가 합성할 필요가 없다.
     */
    private suspend fun segment(source: Bitmap): Bitmap {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        return try {
            // **모델이 준비될 때까지 먼저 기다린다.** 이 줄이 없으면 그 기기의 첫
            // 호출이 반드시 실패한다 ([MODEL_WAIT_MS] 주석 참고).
            withTimeoutOrNull(MODEL_WAIT_MS) { segmenter.initTask.await() }
            val result = segmenter.process(InputImage.fromBitmap(source, 0)).await()
            result.foregroundBitmap ?: error("피사체를 찾지 못했습니다.")
        } finally {
            segmenter.close()
        }
    }

    /**
     * `Task` 를 코루틴으로 기다린다.
     *
     * `kotlinx-coroutines-play-services` 를 넣으면 `await()` 가 딸려 오지만, 이거
     * 하나 때문에 의존성을 늘리지 않는다 — 이 저장소는 네비게이션 라이브러리도
     * 안 쓰고 `HttpURLConnection` 을 직접 쓴다.
     *
     * **실패는 취소가 아니라 예외로 넘긴다.** 취소로 넘기면 부르는 쪽의
     * `runCatching` 이 안 잡아서 왜 물러섰는지가 사라진다.
     */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value -> cont.resume(value) }
        addOnFailureListener { error -> cont.resumeWithException(error) }
        addOnCanceledListener { cont.cancel() }
    }

    // -- 테두리 띠 -----------------------------------------------------------

    /**
     * 알파를 부풀려 띠를 두르고 그 위에 원본을 얹는다. **[subject] 를 지운다.**
     *
     * 부풀리기는 **가로 한 번 · 세로 한 번**으로 나눠서 한다. 사각 이웃을 한 번에
     * 훑으면 반지름 r 에 대해 픽셀당 (2r+1)² 번을 보는데, 나누면 2(2r+1) 번이면 된다.
     * 900px 짜리에서 이 차이가 100ms 와 1초쯤이다.
     */
    private fun withOutline(subject: Bitmap, band: Int = Color.WHITE): Bitmap {
        val w = subject.width
        val h = subject.height
        val radius = (maxOf(w, h) * OUTLINE_RATIO).toInt().coerceAtLeast(1)

        val pixels = IntArray(w * h)
        subject.getPixels(pixels, 0, w, 0, 0, w, h)

        val alpha = ByteArray(w * h) { (pixels[it] ushr 24).toByte() }
        val grown = alpha.dilated(w, h, radius)

        // 띠를 먼저 깔고 원본을 덮는다. 오린 자리의 계단은 띠 안에 먹힌다.
        val bandOnly = IntArray(w * h) { i ->
            val a = grown[i].toInt() and 0xFF
            if (a < ALPHA_FLOOR) 0 else (a shl 24) or (band and 0x00FFFFFF)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(bandOnly, 0, w, 0, 0, w, h)
        Canvas(out).drawBitmap(subject, 0f, 0f, null)
        subject.recycle()
        return out
    }

    /** 분리 가능한 최대값 필터. 알파 채널만 다룬다. */
    private fun ByteArray.dilated(w: Int, h: Int, radius: Int): ByteArray {
        val mid = ByteArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var best = 0
                for (dx in -radius..radius) {
                    val nx = x + dx
                    if (nx < 0 || nx >= w) continue
                    val v = this[row + nx].toInt() and 0xFF
                    if (v > best) best = v
                }
                mid[row + x] = best.toByte()
            }
        }
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var best = 0
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    val v = mid[ny * w + x].toInt() and 0xFF
                    if (v > best) best = v
                }
                out[y * w + x] = best.toByte()
            }
        }
        return out
    }

    // -- 비트맵 손질 ---------------------------------------------------------

    /** 정규화 상자 `[x, y, w, h]` 로 자른다. 밖으로 나가면 안쪽으로 밀어 넣는다. */
    private fun Bitmap.crop(box: FloatArray): Bitmap {
        val x = (box[0] * width).toInt().coerceIn(0, width - 1)
        val y = (box[1] * height).toInt().coerceIn(0, height - 1)
        val w = (box[2] * width).toInt().coerceIn(1, width - x)
        val h = (box[3] * height).toInt().coerceIn(1, height - y)
        return Bitmap.createBitmap(this, x, y, w, h)
    }

    /**
     * 칠해진 영역만 남기고 투명한 여백을 걷어낸다.
     *
     * **이걸 안 하면 카드에서 강아지 크기가 사진마다 달라진다.** 카드는 `fit`
     * 사각형에 누끼를 맞춰 넣는데, 여백이 붙어 있으면 그 여백까지 포함해서 맞춰져서
     * 어떤 카드는 강아지가 작게 앉는다. 방 그림을 "칠해진 영역" 기준으로 세로
     * 정렬하는 것과 같은 이유다.
     */
    private fun Bitmap.trimToContent(): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) < ALPHA_FLOOR) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return this
        if (left == 0 && top == 0 && right == width - 1 && bottom == height - 1) return this
        return Bitmap.createBitmap(this, left, top, right - left + 1, bottom - top + 1)
    }

    /**
     * 세그멘테이션이 안 될 때의 물러섬. 상자를 **타원으로** 잘라낸다.
     *
     * 네모로 두지 않는 이유는, 네모난 사진 조각이 카드에 얹히면 오려낸 것이 아니라
     * 사진을 붙여 넣은 것으로 보이기 때문이다. 타원은 최소한 "얼굴"로 읽힌다.
     */
    private fun Bitmap.ellipseMasked(): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawOval(RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, 0f, 0f, paint)
        return out
    }

    /**
     * 안쪽에 딱 맞는 원 밖을 지운다. 가장자리는 [CIRCLE_FEATHER] 만큼 흐린다 —
     * 딱 자르면 톱니가 보이고, 그러면 오려 붙인 티가 난다.
     */
    private fun Bitmap.circleMasked(): Bitmap {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, 0, 0, w, h)
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f
        val inner = 1f - CIRCLE_FEATHER
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val d = kotlin.math.hypot(x + 0.5f - cx, y + 0.5f - cy) / r
                val k = when {
                    d <= inner -> 1f
                    d >= 1f -> 0f
                    else -> 1f - (d - inner) / CIRCLE_FEATHER
                }
                if (k >= 1f) continue
                val p = pixels[row + x]
                val a = ((p ushr 24) * k).toInt().coerceIn(0, 255)
                pixels[row + x] = (a shl 24) or (p and 0x00FFFFFF)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** 아래 [FADE_HEIGHT] 만큼을 서서히 지운다. **[this] 를 지운다.** */
    private fun Bitmap.fadedBottom(): Bitmap {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, 0, 0, w, h)
        val start = (h * (1f - FADE_HEIGHT))
        for (y in start.toInt().coerceAtLeast(0) until h) {
            val k = (1f - (y - start) / (h - start)).coerceIn(0f, 1f)
            val row = y * w
            for (x in 0 until w) {
                val p = pixels[row + x]
                val a = ((p ushr 24) * k).toInt().coerceIn(0, 255)
                pixels[row + x] = (a shl 24) or (p and 0x00FFFFFF)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        recycle()
        return out
    }

    private fun Bitmap.scaledToFit(edge: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= edge) return this
        val ratio = edge.toFloat() / longest
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }
}
