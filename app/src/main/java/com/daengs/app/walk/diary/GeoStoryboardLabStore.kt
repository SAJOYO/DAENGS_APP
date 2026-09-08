package com.daengs.app.walk.diary

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Isolated debug experiment storage. Never inserts synthetic walks into the user's walk DB. */
class GeoStoryboardLabStore(private val directory: File) {
    data class Saved(val bundle: GeoStoryboardBundle, val draft: StoryboardDraft)
    private fun file(sessionId: String) = File(directory, storyboardHash(sessionId)+".json")

    fun load(sessionId: String): Saved? = file(sessionId).takeIf { it.exists() }?.let {
        decode(it.readBytes()).also { saved -> require(saved.bundle.sessionId == sessionId) }
    }

    fun latest(): Saved? = directory.listFiles()?.filter { it.extension == "json" }
        ?.maxByOrNull { it.lastModified() }?.let { decode(it.readBytes()) }

    fun save(bundle: GeoStoryboardBundle, draft: StoryboardDraft) {
        require(bundle.synthetic) { "개발용 화면에서는 합성 산책만 불러올 수 있어요." }
        check(directory.isDirectory || directory.mkdirs())
        val payload = JSONObject().put("version", 1).put("bundle", JSONObject(bundle.rawJson))
            .put("draft", JSONObject(draft.toJson())).toString().toByteArray(Charsets.UTF_8)
        val temporary = File.createTempFile("storyboard-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { it.write(payload); it.fd.sync() }
            // Same-filesystem atomic replacement; failure leaves the previous review intact.
            // NIO is available at minSdk 26 and also replaces existing files on Windows tests.
            Files.move(temporary.toPath(), file(bundle.sessionId).toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    private fun decode(bytes: ByteArray): Saved {
        val value = JSONObject(bytes.toString(Charsets.UTF_8))
        require(value.getInt("version") == 1)
        val bundle = GeoStoryboardBundle.parse(value.getJSONObject("bundle").toString())
        require(bundle.synthetic)
        return Saved(bundle, StoryboardDraft.parse(value.getJSONObject("draft").toString()))
    }
}
