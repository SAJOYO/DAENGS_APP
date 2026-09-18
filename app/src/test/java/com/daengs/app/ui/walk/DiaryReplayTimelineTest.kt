package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.detail.PreparedDiaryRoute
import com.daengs.app.ui.walk.detail.WalkDiaryReadView
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import com.daengs.app.walk.routeexplorer.*
import org.junit.Assert.*
import org.junit.Test

class DiaryReplayTimelineTest {
    @Test fun `navigation includes start end once and never leaks boundaries into a partial range`() {
        val timeline=DiaryReplayTimeline(listOf(DiaryReplayEvent("a",10),DiaryReplayEvent("b",20)))
        assertEquals(listOf(0L,10L,20L,30L),timeline.navigationStops(30))
        assertEquals(listOf(10L,20L),timeline.navigationStops(30,5,25))
        assertEquals(listOf(0L,30L),DiaryReplayTimeline(emptyList()).navigationStops(30))
    }
    @Test fun `absolute seeking groups simultaneous records and clears future or out of range records`() {
        val timeline=DiaryReplayTimeline(listOf(DiaryReplayEvent("late",30), DiaryReplayEvent("a",10), DiaryReplayEvent("b",10)))
        assertNull(timeline.at(9))
        assertEquals(setOf("a","b"),timeline.at(10)!!.markerIds)
        assertEquals(setOf("late"),timeline.at(40)!!.markerIds)
        assertEquals(setOf("a","b"),timeline.at(15)!!.markerIds)
        assertNull(timeline.at(25,20,35))
        assertEquals(30L,timeline.next(10,35)); assertNull(timeline.next(10,20))
        assertEquals(10L,timeline.previous(30)); assertNull(timeline.previous(30,20))
    }

    @Test fun `end clears the previous checkpoint but retains actual records made exactly at the end`() {
        val prior=DiaryReplayEvent("before",10)
        assertNull(DiaryReplayTimeline(listOf(prior)).current(20,20))
        val end=DiaryReplayEvent("at-end",20)
        assertEquals(setOf(end.id),DiaryReplayTimeline(listOf(prior,end)).current(20,20)!!.markerIds)
    }

    @Test fun `an action scene creates one event and selects only its scene marker at tap time`() {
        val base=explorerPanelPreviewRead()
        val scene=base.diary!!.scenes[1].copy(entryId="a", source=null, content=null)
        val action=WalkEntry("a",scene.sessionId,WalkMomentType.SNIFFING,7_500,
            point=scene.point,locationCapturedAtMillis=0)
        val read=base.copy(diary=base.diary.copy(scenes=listOf(scene),sourceEntries=listOf(action)))
        val timeline=diaryReplayTimeline(read)
        val checkpoint=timeline.checkpoints.single()
        assertEquals(7_500L,checkpoint.elapsed)
        assertEquals(setOf(scene.id),checkpoint.markerIds)
        assertEquals(scene,checkpoint.events.single().scene)
        assertEquals(action,checkpoint.readingEvents().single().action)
        assertEquals(1,checkpoint.events.single().ordinal)
        assertEquals(0,timeline.unresolvedCount)
        assertNull(timeline.at(7_499))
        assertEquals(setOf(scene.id),timeline.at(15_000)!!.markerIds)
    }

    @Test fun `source-reference-only scene is joined before replay events are built`() {
        val base=explorerPanelPreviewRead()
        val source=StoryboardScene("card",15_000,"","","","source",
            entryReference=StoryboardEntryReference("a",null,null))
        val scene=base.diary!!.scenes[1].copy(entryId=null,source=source,content=null)
        val action=WalkEntry("a",scene.sessionId,WalkMomentType.BARKING,15_000)
        val read=base.copy(diary=base.diary.copy(scenes=listOf(scene),sourceEntries=listOf(action)))
        val event=diaryReplayTimeline(read).checkpoints.single().events.single()
        assertEquals(scene.id,event.id)
        assertEquals(action,event.action)
    }

    @Test fun `same-time scenes keep diary order instead of sorting by marker id`() {
        val base=explorerPanelPreviewRead()
        val scene=base.diary!!.scenes[1].copy(source=null,content=null,entryId=null)
        val first=scene.copy(id="${scene.sessionId}/z-first")
        val second=scene.copy(id="${scene.sessionId}/a-second")
        val read=base.copy(diary=base.diary.copy(scenes=listOf(first,second),sourceEntries=emptyList()))
        val events=diaryReplayTimeline(read).checkpoints.single().events
        assertEquals(listOf(first.id,second.id),events.map { it.id })
        assertEquals(listOf(1,2),events.map { it.ordinal })
    }

    @Test fun `unresolved bound action keeps one scene event by falling back to scene timing`() {
        val base=explorerPanelPreviewRead()
        val scene=base.diary!!.scenes[1].copy(entryId="a",source=null,content=null)
        val action=WalkEntry("a",scene.sessionId,WalkMomentType.SNIFFING,Long.MAX_VALUE)
        val read=base.copy(diary=base.diary.copy(scenes=listOf(scene),sourceEntries=listOf(action)))
        val timeline=diaryReplayTimeline(read)
        val event=timeline.checkpoints.single().events.single()
        assertEquals(scene.id,event.id)
        assertEquals(scene,event.scene)
        assertEquals(action,event.action)
        assertEquals(0,timeline.unresolvedCount)
    }

    @Test fun `actions use event time and editable boundaries keep ordinary scene numbering`() {
        val base=explorerPanelPreviewRead()
        val scene=base.diary!!.scenes[1]
        val action=WalkEntry("a",scene.sessionId,WalkMomentType.SNIFFING,7_500,
            point=scene.point, locationCapturedAtMillis=0)
        val read=base.copy(diary=base.diary.copy(scenes=listOf(scene,scene.copy(id="${scene.sessionId}/start")),
            sourceEntries=listOf(action,action.copy(id="unlocated",point=null,locationCapturedAtMillis=null),
                action.copy(id="foreign",sessionId="other"))))
        val timeline=diaryReplayTimeline(read)
        assertEquals(listOf(0L,7_500L,15_000L),timeline.checkpoints.map { it.elapsed })
        assertNull(timeline.at(0)!!.events.single().ordinal)
        assertEquals(2,timeline.at(7_500)!!.events.size)
        assertEquals(1,timeline.at(15_000)!!.events.single().ordinal)
        assertEquals(0,timeline.unresolvedCount)
    }

    @Test fun `pause time is excluded and an event inside the pause is not assigned to a visit`() {
        val base=explorerPanelPreviewRead()
        val detail=base.route.detail
        val fixes=detail.observations.map { if(it.atMillis>=120_000) it.copy(sourceEpoch="second") else it }
        val first=detail.measurement!!.recordingEpochs.single().copy(endedAtMillis=30_000,endedElapsedNanos=30_000_000_000)
        val second=first.copy(id="second",startedAtMillis=120_000,startedElapsedNanos=120_000_000_000,
            endedAtMillis=150_000,endedElapsedNanos=150_000_000_000)
        val measured=detail.measurement.copy(recordingEpochs=listOf(first,second),
            usableSources=fixes.map { it.measurementRef(detail.summary.sessionId) }.toSet(),
            walkingSections=fixes.chunked(3).mapIndexed { i,fs->MeasurementWalkingSection("section-$i",fs.map { it.measurementRef(detail.summary.sessionId) }) })
        val route=PreparedDiaryRoute(detail.copy(observations=fixes,measurement=measured))
        val entries=listOf(135_000L,60_000L).map { WalkEntry("$it",detail.summary.sessionId,WalkMomentType.BARKING,it) }
        val timeline=diaryReplayTimeline(WalkDiaryReadView(route,DiaryWalk(detail.summary,emptyList(),"",sourceEntries=entries)))
        assertEquals(45_000L,timeline.checkpoints.single().elapsed)
        assertEquals(1,timeline.unresolvedCount)
    }

    @Test fun `repeated wall clock needs source identity and does not arbitrarily assign an action`() {
        val base=measuredSceneDetail(secondVisit=true)
        val epochs=(0..1).map { n->RecordingEpoch("epoch-$n","measured","clock-$n",n,0,0,n*5L,
            endedAtMillis=100_000,endedElapsedNanos=12_000_000_000,endKind="stop",drained=true) }
        val detail=base.copy(measurement=base.measurement!!.copy(recordingEpochs=epochs))
        val route=PreparedDiaryRoute(detail)
        val scene=measuredScene(detail,7)
        val action=WalkEntry("a",detail.summary.sessionId,WalkMomentType.SNIFFING,scene.atMillis)
        val read=WalkDiaryReadView(route,DiaryWalk(detail.summary,listOf(scene),"",sourceEntries=listOf(action)),
            mapOf(scene.id to route.review.recordSceneFocus(scene)))
        val timeline=diaryReplayTimeline(read)
        assertEquals(18_000L,timeline.checkpoints.single().elapsed)
        assertEquals(scene.id,timeline.checkpoints.single().events.single().id)
        assertEquals(1,timeline.unresolvedCount)
    }

    @Test fun `highlight projection changes only selection while original markers and coordinates survive`() {
        val read=explorerPanelPreviewRead()
        val scenes=read.diary!!.scenes
        val action=WalkEntry("a",scenes[0].sessionId,WalkMomentType.SNIFFING,0,scenes[0].point,0)
        val diary=read.diary.copy(scenes=scenes.mapIndexed { index,scene ->
            if(index==0) scene.copy(entryId=action.id,source=null,content=null) else scene
        },sourceEntries=listOf(action))
        val base=diarySceneMarkers(diary.scenePresentation(),scenes[0].id)
        val selected=setOf(scenes[1].id,scenes[0].id)
        val marked=diaryReplayMarkers(base,selected)
        assertEquals(selected,marked.filter { it.selected }.map { it.id }.toSet())
        assertEquals(base.map { it.id to it.point },marked.map { it.id to it.point })
        assertEquals(scenes[0].id,base.single { it.selected }.id)
        assertTrue(diaryReplayMarkers(marked,emptySet()).none { it.selected })
    }
}
