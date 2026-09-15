package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkSummary
import org.junit.Assert.*
import org.junit.Test

class DiaryBoundaryScenesTest {
    private val summary=WalkSummary("s",emptyList(),1_000,300_000,null,100.0,299_000,emptyList(),null)
    private val empty=DiaryWalk(summary,emptyList(),"")

    @Test fun `missing bookends can be edited and reloaded with stable roles and actual times`() {
        val initial=empty.withBoundaryScenes(StoryboardDraft())
        var draft=StoryboardDraft()
        initial.scenes.forEach { draft=draft.edit(it.source!!,title="내 제목 ${it.id}",body="내가 쓴 내용",bodyScope=SceneBodyScope.SCENE) }
        val reopened=empty.withBoundaryScenes(StoryboardDraft.parse(draft.toJson()))
        assertEquals(listOf("s/start","s/end"),reopened.scenes.map { it.id })
        assertEquals(listOf(1_000L,300_000L),reopened.scenes.map { it.atMillis })
        assertTrue(reopened.scenes.all { it.body=="내가 쓴 내용" && it.title.startsWith("내 제목") })
        assertEquals(listOf(DiarySceneKind.START,DiarySceneKind.END),reopened.scenes.map { diarySceneKind(it,emptyList(),"s") })
    }

    @Test fun `existing boundary prose is retained once and hidden scenes are not resurrected`() {
        val source=StoryboardScene("geo:start",2_000,"준비하는 마음","보존할 내용","","old")
        val start=DiaryScene("s/geo:start","s",2_000,source.title,source.body,null,"",source=source)
        val board=empty.copy(scenes=listOf(start)).withBoundaryScenes(StoryboardDraft())
        assertEquals(1,board.scenes.count { it.boundaryKind()==DiarySceneKind.START })
        assertEquals("보존할 내용",board.scenes.first().body)
        assertEquals(1_000L,board.scenes.first().atMillis)
        assertSame(source,board.scenes.first().source)
        assertEquals(listOf(DiarySceneKind.END),empty.withBoundaryScenes(StoryboardDraft().hide(source)).scenes.map { it.boundaryKind() })
        assertTrue(empty.copy(preparing=true).withBoundaryScenes(StoryboardDraft()).scenes.isEmpty())
    }

    @Test fun `titles cannot turn ordinary records into bookends`() {
        val scene=DiaryScene("s/note","s",1_000,"산책 시작","산책 끝",null,"")
        assertNull(scene.boundaryKind())
        assertNull(scene.copy(id="s/start",entryId="a").boundaryKind())
    }
}
