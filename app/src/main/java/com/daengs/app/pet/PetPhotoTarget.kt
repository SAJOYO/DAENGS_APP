package com.daengs.app.pet

/**
 * 고른 사진을 **어느 아이에게 걸까.**
 *
 * 고치기로 들어왔으면 그 아이다. 새로 등록했으면 **id 를 서버가 만들기 때문에**
 * 폼에서는 알 수가 없다 — 등록하고 목록을 다시 받은 뒤에 **늘어난 하나**를 찾는다.
 *
 * **틀리면 남의 아이 얼굴에 사진이 붙는다.** 화면에서는 "왜 다른 애 사진이지" 로만
 * 보이고 원인이 안 보이는 종류라, 이 셈만 따로 꺼내 둔다.
 *
 * @param editingId 고치는 중인 아이. null 이면 새로 등록한 것이다
 * @param before 등록하기 **전에** 있던 id 들
 * @param after 등록하고 다시 받아 온 id 들
 * @return 사진을 걸 아이. **못 고르면 null** — 늘어난 것이 없거나 둘 이상이면
 *   아무 데나 거는 것보다 안 거는 편이 낫다
 */
fun photoTargetId(editingId: String?, before: Set<String>, after: List<String>): String? {
    if (editingId != null) return editingId.takeIf { it in after }
    val added = after.filterNot { it in before }
    // 둘 이상 늘어난 것은 우리가 모르는 일이 벌어진 것이다 (다른 기기에서 등록했다든가).
    // 그때 아무거나 고르면 남의 아이에게 붙는다.
    return added.singleOrNull()
}
