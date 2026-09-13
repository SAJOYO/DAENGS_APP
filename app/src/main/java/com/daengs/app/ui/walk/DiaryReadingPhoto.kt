package com.daengs.app.ui.walk

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Only the selected scene's real local photo is decoded; a missing file is never replaced by art. */
@Composable
internal fun DiaryReadingPhoto(photo: WalkPhoto, onOpen: () -> Unit) = key(photo.sessionId, photo.id, photo.file) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            try { com.daengs.app.screening.Photo.decodeUpright(context, Uri.fromFile(photo.file), 800) }
            catch (e: Exception) { if (e is CancellationException) throw e; null }
        }
        loaded = true
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        bitmap?.let { image ->
            Surface(onClick = onOpen, shape = RoundedCornerShape(12.dp), color = PinkFaint) {
                Image(image.asImageBitmap(), "산책 중 촬영한 사진",
                    Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit)
            }
        } ?: Text(if (loaded) "사진 파일을 읽을 수 없어요." else "사진을 불러오는 중…",
            style = MaterialTheme.typography.bodySmall, color = TextMuted)
        TextButton(onClick = onOpen) { Text("사진 보기") }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun DiaryReadingPhotoPreview() { DaengsTheme {
    DiaryReadingPhoto(WalkPhoto("preview", "preview", 0, GeoPoint(37.5, 127.0), File("unavailable.jpg")), {})
} }
