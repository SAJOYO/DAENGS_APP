package com.daengs.app.walk.diary

/** Presentation boundary: source pieces become one editable scene, never separate reader sections. */
fun StoryboardScene.sceneBody(): String {
    if (bodyScope == SceneBodyScope.SCENE) return body // Empty is also an intentional edit.
    val material = diary ?: return body
    val record = when (material.recordKind) {
        "behavior" -> when (material.recordText) {
            "킁킁" -> "냄새를 맡았다."
            "배변" -> "배변을 했다."
            "짖음" -> "짖었다."
            else -> material.recordText
        }
        "photo" -> "사진을 남겼다."
        // Describe the measured movement, without assigning a cause or a dog's intention.
        "observed_dwell" -> "한곳에 머문 구간이 있었다."
        "observed_fast" -> "이동이 빨라진 구간이 있었다."
        "observed_slow" -> "이동이 느려진 구간이 있었다."
        else -> material.recordText // Preserve note whitespace and line breaks.
    }
    if (body.isBlank()) return record
    if (record.isBlank() || body.trim() == record.trim()) return body
    return body.trimEnd() + " " + record
}
