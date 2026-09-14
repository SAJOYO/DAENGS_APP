package com.daengs.app.walk.diary

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryInputBoundaryTest {
    @Test fun `pure diary inputs assembly and board rules do not reference storage or sync`() {
        val sources = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val diary = File(sources, "com/daengs/app/walk/diary")
        val files = listOf("DiaryInputs.kt", "WalkDiaryAssembly.kt", "WalkStoryboardAnalysis.kt", "LocalDiaryBoard.kt")
            .map { File(diary, it) }
        assertTrue("Every protected calculation file must exist", files.all { it.isFile })
        val forbidden = Regex("\\b(?:com\\s*\\.\\s*daengs\\s*\\.\\s*app\\s*\\.\\s*walk\\s*\\.\\s*(?:store|sync)|androidx\\s*\\.\\s*room)\\b")
        val violations = files.filter { forbidden.containsMatchIn(it.readText()) }
        assertTrue("Convert persistence inputs in the store adapter: " + violations.joinToString { it.name },
            violations.isEmpty())
    }
}
