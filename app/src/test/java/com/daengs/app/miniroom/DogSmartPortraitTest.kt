package com.daengs.app.miniroom

import com.daengs.app.miniroom.art.DogBreed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 챗봇 얼굴(`smartRes`)이 견종과 **어긋나지 않는가.**
 *
 * 그림 파일은 저쪽에서 한글 이름으로 온다(`똑똑이비글.png`). 한 번은 닥스훈트 셋이
 * `똑똑이장모베이지치와와` 처럼 **치와와**로 붙어 왔는데, 앱에는 치와와가 한 종이고
 * 닥스훈트가 세 종이라 그대로 믿었으면 세 종이 밀렸다.
 *
 * 밀려도 앱은 안 죽는다 — **그냥 남의 견종 얼굴이 챗봇에 뜬다.** 눈으로만 잡히는
 * 종류라 여기서 세어 둔다. 반입은 `tools/import_smart_portraits.py` 가 한다.
 */
class DogSmartPortraitTest {

    @Test
    fun `견종마다 챗봇 얼굴이 있다`() {
        DogBreed.ALL.forEach {
            assertTrue("${it.id} 에 smartRes 가 없다", it.smartRes != 0)
        }
    }

    /** 같으면 반입이 안 된 것이고, 챗봇에 학사모 없는 얼굴이 뜬다. */
    @Test
    fun `챗봇 얼굴이 프로필 얼굴과 다르다`() {
        DogBreed.ALL.forEach {
            assertTrue("${it.id} 가 프로필 그림을 그대로 쓰고 있다", it.smartRes != it.portraitRes)
        }
    }

    /**
     * 스물일곱 장이 **서로 겹치지 않는다.**
     *
     * 한글 이름 매핑이 겹치면 두 견종이 같은 그림을 가리키게 되는데, 표를 눈으로
     * 훑어서는 안 보인다. 개수로 잡는다.
     */
    @Test
    fun `챗봇 얼굴이 견종마다 다르다`() {
        val used = DogBreed.ALL.map { it.smartRes }.toSet()
        assertEquals("겹치는 그림이 있다", DogBreed.ALL.size, used.size)
    }
}
