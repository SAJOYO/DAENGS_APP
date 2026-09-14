package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.reading.DiaryReviewTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.*

internal enum class DiaryReadingExample { LIST, BODY, LONG_TITLE, MULTI_DOG, REMOTE_PHOTO, EMPTY, LOADING, ERROR, ADDRESS, BACKUP, LOADING_ERROR }
internal class DiaryReadingExamples : PreviewParameterProvider<DiaryReadingExample> {
    override val values = DiaryReadingExample.entries.asSequence()
}

/** Production reader with explicitly synthetic content; the map is an SDK placeholder. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
internal fun DiaryReadingStylePreview(@PreviewParameter(DiaryReadingExamples::class) example: DiaryReadingExample) {
    val at = 1_789_203_720_000L
    val pet = Pet("a", "보리", DogBreed.SHIBA_INU_ORANGE.id, null, null, null, null, null, isPrimary = false)
    val ids = if (example == DiaryReadingExample.MULTI_DOG) listOf("a", "b", "missing") else listOf("a")
    val pets = listOf(pet, pet.copy(id = "b", name = "두부", breed = "mix"))
    val loading = example in setOf(DiaryReadingExample.LOADING, DiaryReadingExample.LOADING_ERROR)
    val error = when (example) {
        DiaryReadingExample.ERROR -> "장면을 불러오지 못했어요. 다시 시도해 주세요."
        DiaryReadingExample.LOADING_ERROR -> "장면 갱신을 마치지 못했어요. 연결 상태를 확인하고 다시 불러와 주세요."
        else -> null
    }
    var backupRequested by remember { mutableStateOf(false) }
    val summary = WalkSummary("preview", ids, at, at + 1_920_000, null, 1800.0, 1_800_000, emptyList(), null)
    val first = DiaryScene("preview/a", "preview", at + 300_000, "풀 냄새에 잠깐 멈춤", "풀잎 앞에서 남긴 킁킁 기록.", null, "")
    val second = first.copy(id = "preview/b", atMillis = at + 600_000,
        title = if (example == DiaryReadingExample.LONG_TITLE) "벤치 앞에서 보리와 잠시 쉬었다가 다시 함께 걸었던 저녁의 기록" else "물 한 모금, 잠깐의 쉼",
        body = "벤치 옆에서 물을 마셨어요.\n잠깐 쉬었다가 다시 함께 걸었어요.\n".repeat(8),
        content = when (example) {
            DiaryReadingExample.REMOTE_PHOTO -> DiarySceneContent("", "photo", photoId = "remote-photo", locationLabel = "확인된 위치 없음")
            DiaryReadingExample.ADDRESS -> DiarySceneContent("", "note", locationLabel = "기록한 위치", address = "서울특별시 종로구 세종대로 광화문광장 북쪽 보행로에서 함께 남긴 위치 설명")
            else -> null
        })
    val scenes = if (loading || example in setOf(DiaryReadingExample.EMPTY, DiaryReadingExample.ERROR)) emptyList() else listOf(first, second)
    var selected by remember(example) { mutableStateOf(second.takeIf { example in setOf(DiaryReadingExample.BODY, DiaryReadingExample.LONG_TITLE, DiaryReadingExample.REMOTE_PHOTO, DiaryReadingExample.ADDRESS) }) }
    DiaryReviewTheme {
        WalkDiaryMapContent(scenes, selected, loading, error,
            onSelect = { selected = it }, onClose = { selected = null }, onEdit = {}, onPhoto = {}, onRetry = {}, onAdd = {},
            onDelete = {}, title = if (example == DiaryReadingExample.LONG_TITLE) "보리와 함께 오래도록 기억하고 싶은 저녁 산책의 기록" else "보리와 노을 한 바퀴",
            subtitle = formatWalkDay(at), walkDogIds = ids, walkPets = pets,
            sceneKinds = mapOf(first.id to DiarySceneKind.SNIFFING, second.id to if (example == DiaryReadingExample.REMOTE_PHOTO) DiarySceneKind.PHOTO else DiarySceneKind.NOTE),
            backupAction = { if (example == DiaryReadingExample.BACKUP) WalkRouteBackupIcon(
                com.daengs.app.walk.sync.WalkRouteBackupState.NEEDS_RETRY, requested = backupRequested, onRequest = { backupRequested = true }) },
            generationNotice = "저장한 장면을 보여드려요.".takeIf { example == DiaryReadingExample.BACKUP },
            mapView = DiaryMapView.WALKING,
            mapLegend = { ObservedRouteLegend(listOf(com.daengs.app.map.layers.completedroute.RecordRouteRole.OBSERVED_EXCLUDED)) },
            summaryContent = { WalkSessionSummary(summary, compact = true) }, explorerPanel = { Text("동선 탐색") },
            selectedRouteNotice = if (selected != null) "이 장면에는 확인된 위치가 없어요." else null,
            map = { Box(Modifier.fillMaxSize().background(PinkFaint), contentAlignment = Alignment.TopCenter) {
                Text("지도 영역 · 미리보기", color = TextMuted)
            } })
    }
}
