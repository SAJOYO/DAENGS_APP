package com.daengs.app.ui.walk.detail

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryReadBoundaryTest {
    @Test fun `detail reading does not depend on walk UI state or storage implementations`() {
        val sources = listOf(File("src"), File("app/src")).first { it.isDirectory }
        val files = listOf("main", "debug", "release").flatMap { sourceSet ->
            File(sources, "$sourceSet/java/com/daengs/app/ui/walk/detail").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("Read source discovery must not silently skip the boundary", files.isNotEmpty())
        val upperUi = Regex("\\bcom\\s*\\.\\s*daengs\\s*\\.\\s*app\\s*\\.\\s*ui\\s*\\.\\s*walk\\b(?!\\s*\\.\\s*detail\\b)")
        val persistence = Regex("\\bcom\\s*\\.\\s*daengs\\s*\\.\\s*app\\s*\\.\\s*(?:DaengsApp\\b|walk\\s*\\.\\s*(?:store|sync)\\b)")
        val violations = files.filter { file ->
            file.readText().let { upperUi.containsMatchIn(it) || persistence.containsMatchIn(it) }
        }
        assertTrue("Keep UI adoption outside detail reading and inject data operations: " +
            violations.joinToString { it.relativeTo(sources).path }, violations.isEmpty())
    }
}
