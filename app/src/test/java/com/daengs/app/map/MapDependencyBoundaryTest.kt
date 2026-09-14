package com.daengs.app.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDependencyBoundaryTest {
    @Test fun `map sources including previews do not reference walk screens`() {
        val sources = listOf(File("src"), File("app/src")).first { it.isDirectory }
        val files = listOf("main", "debug", "release").flatMap { sourceSet ->
            File(sources, "$sourceSet/java/com/daengs/app/map").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("Map source discovery must not silently skip the boundary check", files.isNotEmpty())
        val walkUi = Regex("\\bcom\\s*\\.\\s*daengs\\s*\\.\\s*app\\s*\\.\\s*ui\\s*\\.\\s*walk\\b")
        val violations = files.filter { walkUi.containsMatchIn(it.readText()) }
        assertTrue("Move shared presentation below the screen; keep screen previews in UI: " +
            violations.joinToString { it.relativeTo(sources).path }, violations.isEmpty())
    }
}
