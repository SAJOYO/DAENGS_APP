package com.daengs.app.dogcard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 카드 한 장을 다른 앱으로 보낸다.
 *
 * **갤러리에 안 남긴다.** 공유만 하고 싶은 사람의 사진첩을 우리가 채우면 안 된다.
 * 캐시에 잠깐 쓰고 `FileProvider` 로 건네준 뒤 다음 공유 때 지운다.
 *
 * **글을 안 붙인다.** `EXTRA_TEXT` 로 앱 소개 한 줄을 같이 보내면 카톡에서 그림과
 * 별개의 말풍선이 하나 더 간다. 자랑하려고 보낸 그림 옆에 광고가 붙는 꼴이다.
 *
 * Intent 를 **만드는 함수와 던지는 함수를 나눠 둔다** — `ui/chat/ReportAnswer.kt` 가
 * 같은 꼴이다. 만드는 쪽이 순수해야 테스트가 플래그 하나 빠진 것을 잡는다.
 */

/** 공유용 그림이 잠깐 머무는 곳. `res/xml/file_paths.xml` 이 여는 딱 그 폴더다. */
private const val SHARE_DIR = "cards"

internal fun cardShareDir(context: Context): File = File(context.cacheDir, SHARE_DIR)

/**
 * 공유용 파일 이름.
 *
 * **`cards/` 밖으로 못 나가게 한다.** [cardFileName] 이 이미 걸러 주지만 여기서도
 * 막는다 — 이 이름이 `FileProvider` 가 여는 범위를 정하는 자리라, 한쪽만 믿으면
 * 나중에 부르는 곳이 늘었을 때 남의 앱에 엉뚱한 파일이 나간다.
 */
internal fun safeShareName(fileName: String): String {
    val bare = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
    val kept = bare.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    return "${kept.ifEmpty { "card" }}.png"
}

internal fun cardShareFile(context: Context, fileName: String): File =
    File(cardShareDir(context), safeShareName(fileName))

/**
 * 그림을 캐시에 써서 남에게 건넬 주소로 바꾼다.
 *
 * 쓰기 전에 옛 파일을 지운다. 캐시라 시스템이 치우기도 하지만, 스무 장을 공유하고 나서
 * 스무 장이 남아 있을 이유가 없다.
 */
fun writeShareFile(context: Context, bitmap: Bitmap, fileName: String): Uri? = runCatching {
    val dir = cardShareDir(context)
    dir.listFiles()?.forEach { it.delete() }
    dir.mkdirs()
    val file = cardShareFile(context, fileName)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

/**
 * 그림 하나를 보내는 Intent.
 *
 * **읽기 권한 플래그가 핵심이다.** 빠지면 공유 시트는 뜨는데 받는 앱에서 그림이
 * 안 열린다 — 시트가 떴으니 된 줄 알기 쉬운 실패다.
 */
fun shareCardIntent(uri: Uri): Intent =
    Intent(Intent.ACTION_SEND)
        .setType(PNG_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

/** 공유 시트를 띄운다. 받을 앱이 하나도 없으면 false. */
fun shareCard(context: Context, uri: Uri): Boolean = runCatching {
    // 고르는 창 자체에도 권한을 얹는다. 안 얹으면 기기에 따라 미리보기가 빈다.
    val chooser = Intent.createChooser(shareCardIntent(uri), null)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
    true
}.getOrDefault(false)
