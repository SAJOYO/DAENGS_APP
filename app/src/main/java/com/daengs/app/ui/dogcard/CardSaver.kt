package com.daengs.app.ui.dogcard

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.daengs.app.dogcard.PNG_MIME
import com.daengs.app.dogcard.saveToGallery
import com.daengs.app.dogcard.shareCard
import com.daengs.app.dogcard.usesMediaStore
import com.daengs.app.dogcard.writeShareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 카드 한 장을 앱 밖으로 꺼낸다 — **사진첩에 넣거나, 남에게 보내거나.**
 *
 * 예전에는 저장할 곳을 고르는 창(SAF)만 있었다. 창이 뜨고 폴더를 고르고 이름을
 * 확인해야 하는데 그러고도 사진첩에는 안 보인다. 지금은 안드로이드 10 이상이면
 * **창 없이 `Pictures/댕스` 앨범**으로 바로 간다 (`dogcard/CardGallery.kt`).
 * 그 아래 기기는 새 권한을 받지 않으려고 예전 창을 그대로 쓴다 — [toGallery] 가
 * 어느 쪽인지 알려 주므로 **화면은 버튼 글씨를 그에 맞게 고른다.**
 *
 * 상태는 두 개다. [busy] 는 그리고 쓰는 동안, [note] 는 끝난 뒤 한 줄. 둘 다 화면이
 * 읽어서 보여 준다 — 저장은 아무 표시가 없으면 **눌렀는지도 모르는** 동작이다.
 * 공유는 시트가 떠서 스스로 말하므로 성공했을 때 한 줄을 안 남긴다.
 */
@Stable
class CardSaver internal constructor(
    /** 사진첩으로 바로 가는 기기인가. 아니면 저장할 곳을 묻는 창이 뜬다 */
    val toGallery: Boolean = usesMediaStore(),
) {

    /** 그리고 쓰는 중. 그동안 버튼을 다시 못 누르게 한다 */
    var busy: Boolean by mutableStateOf(false)
        internal set

    /** 끝난 뒤 한 줄. 잠시 뒤 저절로 사라진다 */
    var note: String? by mutableStateOf(null)
        internal set

    internal var pending: CardShot? by mutableStateOf(null)
    internal var run: ((CardShot, Task) -> Unit)? = null

    /** 사진첩에 넣는다. 옛 기기에서는 저장할 곳을 묻는 창이 먼저 뜬다. */
    fun save(shot: CardShot) = start(shot, Task.Save)

    /** 공유 시트를 띄운다. 갤러리에는 안 남는다. */
    fun share(shot: CardShot) = start(shot, Task.Share)

    private fun start(shot: CardShot, task: Task) {
        if (busy) return
        note = null
        run?.invoke(shot, task)
    }

    internal enum class Task { Save, Share }
}

/**
 * 내보낼 카드 한 장.
 *
 * **저장과 공유가 같은 것을 받는다.** 인자를 여섯 개씩 두 벌 적어 두면 한쪽만
 * 고쳐지고, 그러면 저장한 카드와 공유한 카드가 다르게 나온다.
 *
 * @param art 자리를 비운 원화. [template] · [face] 가 있으면 여기에 얼굴을 끼운다
 * @param template null 이면 **[art] 를 그대로** 내보낸다 — 얼굴 없이 원래 카드가
 *   그림인 경우(개발 기기의 시드 열두 장, 누끼 파일이 지워진 카드)가 그렇다.
 *   그런 카드에 우리 이름을 얹으면 이미 인쇄된 글자 위에 겹쳐 찍힌다
 */
data class CardShot(
    val fileName: String,
    val art: ImageBitmap,
    val template: CardTemplate?,
    val face: CardFace?,
    val name: String,
    val code: String,
)

/** 알림 한 줄이 남아 있는 시간. */
private const val NOTE_MS = 2600L

private const val SAVED_GALLERY = "갤러리에 저장했어요"
private const val SAVED_FILE = "이미지로 저장했어요"
private const val SAVE_FAILED = "저장하지 못했어요"
private const val SHARE_FAILED = "공유하지 못했어요"

@Composable
fun rememberCardSaver(): CardSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 화면이 글자를 재는 그 도구다. 여기서 새로 만들지 않는 것이 중요하다 —
    // 밀도가 다르면 파일 속 글자만 커지거나 작아진다.
    val measurer = rememberTextMeasurer()
    val saver = remember { CardSaver() }

    // 옛 기기(API 28 이하)에서만 쓰는 길. 저장할 곳을 고르고 나서야 그린다.
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PNG_MIME),
    ) { uri ->
        val shot = saver.pending
        saver.pending = null
        if (uri == null || shot == null) return@rememberLauncherForActivityResult
        saver.busy = true
        scope.launch {
            saver.note = deliver(context, measurer, shot, CardSaver.Task.Save, uri)
            saver.busy = false
        }
    }

    DisposableEffect(create) {
        saver.run = { shot, task ->
            if (task == CardSaver.Task.Save && !saver.toGallery) {
                saver.pending = shot
                create.launch(shot.fileName)
            } else {
                saver.busy = true
                scope.launch {
                    saver.note = deliver(context, measurer, shot, task, target = null)
                    saver.busy = false
                }
            }
        }
        onDispose { saver.run = null }
    }
    LaunchedEffect(saver.note) {
        if (saver.note == null) return@LaunchedEffect
        delay(NOTE_MS)
        saver.note = null
    }
    return saver
}

/**
 * 그려서 내보내고, 화면에 남길 한 줄을 돌려준다.
 *
 * **그리는 것은 부른 자리(주 스레드)에서 한다.** `TextMeasurer` 는 합성이 만들어 준
 * 것이라 다른 스레드로 넘기지 않는다. 1080x1440 한 장은 몇 ms 다. 파일을 쓰는 것만
 * IO 로 넘긴다.
 *
 * @param target SAF 로 고른 자리. null 이면 사진첩이나 공유가 알아서 자리를 만든다
 * @return 알림 한 줄. 성공한 공유는 시트가 스스로 말하므로 null 이다
 */
private suspend fun deliver(
    context: Context,
    measurer: TextMeasurer,
    shot: CardShot,
    task: CardSaver.Task,
    target: Uri?,
): String? {
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
    }.getOrNull() ?: return if (task == CardSaver.Task.Share) SHARE_FAILED else SAVE_FAILED

    val (bitmap, ours) = made
    // 공유는 파일을 쓰는 데까지만 IO 다. 시트를 띄우는 것은 주 스레드로 돌아와서 한다.
    val shared = if (task == CardSaver.Task.Share) {
        withContext(Dispatchers.IO) { writeShareFile(context, bitmap, shot.fileName) }
    } else {
        null
    }
    val ok = when {
        task == CardSaver.Task.Share -> shared != null && shareCard(context, shared)
        target != null -> withContext(Dispatchers.IO) { writeTo(context, target, bitmap) }
        else -> withContext(Dispatchers.IO) { saveToGallery(context, bitmap, shot.fileName) != null }
    }
    // 우리가 만든 것만 놓는다.
    if (ours) bitmap.recycle()

    return when {
        !ok && task == CardSaver.Task.Share -> SHARE_FAILED
        !ok -> SAVE_FAILED
        task == CardSaver.Task.Share -> null
        target != null -> SAVED_FILE
        else -> SAVED_GALLERY
    }
}

/** 사용자가 고른 자리에 쓴다 (API 28 이하). */
private fun writeTo(context: Context, uri: Uri, bitmap: Bitmap): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    } ?: false
}.getOrDefault(false)
