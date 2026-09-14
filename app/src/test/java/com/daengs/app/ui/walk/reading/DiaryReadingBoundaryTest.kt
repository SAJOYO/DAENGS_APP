package com.daengs.app.ui.walk.reading

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryReadingBoundaryTest {
    @Test fun `reading components do not depend on walk screens or persistence orchestration`() {
        val sources = listOf(File("src"), File("app/src")).first { it.isDirectory }
        val files = listOf("main", "debug", "release").flatMap { sourceSet ->
            File(sources, "$sourceSet/java/com/daengs/app/ui/walk/reading").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("Reading source discovery must not silently skip the boundary", files.isNotEmpty())
        val upperUi = Regex("\\bcom\\s*\\.\\s*daengs\\s*\\.\\s*app\\s*\\.\\s*ui\\s*\\.\\s*walk\\b(?!\\s*\\.\\s*reading\\b)")
        val persistence = Regex("\\bcom\\s*\\.\\s*daengs\\s*\\.\\s*app\\s*\\.\\s*(?:DaengsApp\\b|walk\\s*\\.\\s*(?:store|sync|detail)\\b)")
        val violations = files.filter { file ->
            file.readText().let { upperUi.containsMatchIn(it) || persistence.containsMatchIn(it) }
        }
        assertTrue("Pass display values and callbacks into reading components: " +
            violations.joinToString { it.relativeTo(sources).path }, violations.isEmpty())
    }
}
