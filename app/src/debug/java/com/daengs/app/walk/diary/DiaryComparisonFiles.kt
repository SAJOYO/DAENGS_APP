package com.daengs.app.walk.diary

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Private ADB exchange for one explicitly selected debug comparison. Never reads account tokens. */
internal object DiaryComparisonFiles {
    suspend fun prepare(context: Context, snapshot: DiaryComparisonSnapshot) = withContext(Dispatchers.IO) {
        val dir = File(context.noBackupFilesDir, "diary-place-comparison").apply { mkdirs() }
        File(dir, "request.json").writeText(snapshot.json, Charsets.UTF_8)
    }

    suspend fun read(context: Context, snapshot: DiaryComparisonSnapshot): DiaryPlaceComparison = withContext(Dispatchers.IO) {
        val result = File(context.noBackupFilesDir, "diary-place-comparison/result.json")
        check(result.isFile) { "아직 준비된 장소 설명이 없어요. PC에서 생성한 뒤 결과를 확인해 주세요." }
        check(result.length() <= 256_000) { "비교 결과가 너무 커요." }
        DiaryPlaceComparison.parse(result.readText(Charsets.UTF_8), snapshot)
    }
}
