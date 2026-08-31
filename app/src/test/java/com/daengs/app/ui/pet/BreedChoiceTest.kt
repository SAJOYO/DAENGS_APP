package com.daengs.app.ui.pet

import com.daengs.app.miniroom.art.DogBreed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 견종 고르기에 무엇이 어떤 순서로 나오는가.
 *
 * 화면은 실기기에서 보지만, **여기 규칙 셋은 눈으로 못 지킨다** — 28개를 매번 세어
 * 보지 않고, 이름이 빈 항목은 얼굴만 있던 옛 상태와 구분이 안 된다.
 */
class BreedChoiceTest {

    /**
     * 27종에서 자기 개를 못 찾은 사람이 아무거나 고르면 **그 값은 데이터로 못 쓴다.**
     * 맨 앞에 두는 건 그래서다. 목록 끝으로 밀리면 스물여덟 번째라 아무도 못 본다.
     */
    @Test
    fun `믹스가 맨 앞이다`() {
        val first = breedChoices().first()
        assertEquals(MIX_BREED, first.id)
        assertNull("믹스는 얼굴 그림이 없다", first.breed)
    }

    @Test
    fun `믹스와 견종 27종이 다 있다`() {
        val choices = breedChoices()
        assertEquals(DogBreed.ALL.size + 1, choices.size)
        assertEquals(DogBreed.ALL.map { it.id }, choices.drop(1).map { it.id })
    }

    /**
     * **이 화면의 요점이 이름이다.** 예전엔 얼굴만 늘어놓아서 푸들 세 색을 그림으로
     * 구분해야 했다. 빈 이름이 하나라도 있으면 그 칸은 옛 상태로 돌아간다.
     */
    @Test
    fun `모든 항목에 이름이 있다`() {
        for (choice in breedChoices()) {
            assertTrue("${choice.id} 에 이름이 없다", choice.label.isNotBlank())
        }
    }

    /** 이름이 겹치면 두 칸이 같은 말을 한다 — 색 변형(실버·연갈색·초코)이 그 위험이다. */
    @Test
    fun `이름이 서로 겹치지 않는다`() {
        val labels = breedChoices().map { it.label }
        assertEquals(labels.size, labels.distinct().size)
    }
}
