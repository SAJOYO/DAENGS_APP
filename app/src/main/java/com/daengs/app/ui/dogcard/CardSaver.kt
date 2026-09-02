package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 카드 한 장을 기기의 파일로 내보낸다.
 *
 * **권한을 안 쓴다.** 저장 위치를 사용자가 직접 고르는 방식이라(SAF) 새 권한이 필요
 * 없고 minSdk 26 에서도 된다 — 턴테이블의 곡 내려받기가 쓰는 그 방식 그대로다.
 * `MediaStore` · `DownloadManager` 는 이 저장소에 전례가 없어서 안 만든다.
 *
 * 상태는 두 개다. [busy] 는 그리고 쓰는 동안, [note] 는 끝난 뒤 한 줄. 둘 다 화면이
 * 읽어서 보여 준다 — 저장은 아무 표시가 없으면 **눌렀는지도 모르는** 동작이다.
 */
@Stable
class CardSaver internal constructor() {

    /** 그리고 쓰는 중. 그동안 버튼을 다시 못 누르게 한다 */
    var busy: Boolean by mutableStateOf(false)
        internal set

    /** 끝난 뒤 한 줄. 잠시 뒤 저절로 사라진다 */
    var note: String? by mutableStateOf(null)
        internal set

    internal var pending: Shot? by mutableStateOf(null)
    internal var open: ((String) -> Unit)? = null

    /**
     * 저장할 곳을 묻는 창을 연다.
     *
     * @param art 자리를 비운 원화. [template] · [face] 가 있으면 여기에 얼굴을 끼운다
     * @param template null 이면 **[art] 를 그대로** 내보낸다 — 얼굴 없이 원래 카드가
     *   그림인 경우(개발 기기의 시드 열두 장, 누끼 파일이 지워진 카드)가 그렇다.
     *   그런 카드에 우리 이름을 얹으면 이미 인쇄된 글자 위에 겹쳐 찍힌다
     */
    fun save(
        fileName: String,
        art: ImageBitmap,
        template: CardTemplate?,
        face: CardFace?,
        name: String,
        code: String,
    ) {
        if (busy) return
        pending = Shot(art, template, face, name, code)
        note = null
        open?.invoke(fileName)
    }

    internal class Shot(
        val art: ImageBitmap,
        val template: CardTemplate?,
        val face: CardFace?,
        val name: String,
        val code: String,
    )
}

/** 알림 한 줄이 남아 있는 시간. */
private const val NOTE_MS = 2600L

@Composable
fun rememberCardSaver(): CardSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 화면이 글자를 재는 그 도구다. 여기서 새로 만들지 않는 것이 중요하다 —
    // 밀도가 다르면 파일 속 글자만 커지거나 작아진다.
    val measurer = rememberTextMeasurer()
    val saver = remember { CardSaver() }

    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val shot = saver.pending
        saver.pending = null
        if (uri == null || shot == null) return@rememberLauncherForActivityResult
        saver.busy = true
        scope.launch {
            // **그리는 것은 이 자리(주 스레드)에서 한다.** `TextMeasurer` 는 합성이
            // 만들어 준 것이라 다른 스레드로 넘기지 않는다. 1080x1440 한 장은 몇 ms 다.
            val made = runCatching {
                if (shot.template != null) {
                    renderCard(
                        art = shot.art,
                        template = shot.template,
                        face = shot.face,
                        measurer = measurer,
                        name = shot.name,
                        code = shot.code,
                    ) to true
                } else {
                    // 화면에 걸린 비트맵이다. **여기서 recycle 하면 그리던 화면이 죽는다.**
                    shot.art.asAndroidBitmap() to false
                }
            }.getOrNull()

            val ok = made != null && withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        made.first.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } ?: error("저장할 곳을 열지 못했습니다.")
                }.isSuccess
            }
            // 우리가 만든 것만 놓는다.
            if (made != null && made.second) made.first.recycle()

            saver.busy = false
            saver.note = if (ok) "이미지로 저장했어요" else "저장하지 못했어요"
        }
    }

    DisposableEffect(create) {
        saver.open = { create.launch(it) }
        onDispose { saver.open = null }
    }
    LaunchedEffect(saver.note) {
        if (saver.note == null) return@LaunchedEffect
        delay(NOTE_MS)
        saver.note = null
    }
    return saver
}
