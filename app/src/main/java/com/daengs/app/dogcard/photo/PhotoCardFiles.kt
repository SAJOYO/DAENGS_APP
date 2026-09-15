package com.daengs.app.dogcard.photo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 완성된 포토 카드 PNG 를 기기에 둔다. 방 액자·저장·다시 열 때 서버에서 또 안 받으려고.
 *
 * **폴더를 받는다** (`filesDir/photo-cards`). `Context` 를 안 받는 것은 JVM 테스트가 임시
 * 폴더로 돌게 하려는 것이다.
 */
class PhotoCardFiles(private val dir: File) {

    private fun file(id: String) = File(dir.apply { mkdirs() }, "$id.png")

    fun existing(): Map<String, File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
            ?.associateBy { it.name.removeSuffix(".png") }
            .orEmpty()

    /** 다 쓴 뒤에만 제자리 이름으로 옮긴다 — 반쯤 쓴 파일이 칸에 뜨면 깨진 그림이 된다. */
    suspend fun write(id: String, png: ByteArray): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(id)
            val part = File(target.parentFile, "$id.part")
            part.writeBytes(png)
            if (!part.renameTo(target)) {
                target.delete()
                check(part.renameTo(target)) { "그림을 저장하지 못했어요." }
            }
            target
        }.getOrNull()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { file(id).delete(); Unit }

    suspend fun keepOnly(ids: Set<String>) = withContext(Dispatchers.IO) {
        existing().filterKeys { it !in ids }.values.forEach { it.delete() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dir.deleteRecursively(); Unit }
}
