package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.detail.PreparedDiaryRoute
import com.daengs.app.ui.walk.detail.WalkDiaryReadView
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryWalk
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

    @Test fun `an action described by a scene reads once without merging original marker identities`() {
        val scene=explorerPanelPreviewRead().diary!!.scenes[1].copy(entryId="a")
        val action=WalkEntry("a",scene.sessionId,WalkMomentType.SNIFFING,scene.atMillis)
        val checkpoint=DiaryReplayCheckpoint(15_000,listOf(DiaryReplayEvent(scene.id,15_000,scene,1),
            DiaryReplayEvent(diaryActionKey(action),15_000,action=action)))
        assertEquals(setOf(scene.id,diaryActionKey(action)),checkpoint.markerIds)
        assertEquals(scene,checkpoint.readingEvents().single().scene)
        assertEquals(action,checkpoint.readingEvents().single().action)
        val unrelated=checkpoint.copy(events=checkpoint.events.map { if(it.scene!=null) it.copy(scene=scene.copy(entryId=null)) else it })
        assertEquals(2,unrelated.readingEvents().size)
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
        val base=diarySceneMarkers(scenes,scenes[0].id) + diaryActionObjects(listOf(action),action.sessionId,emptySet())
        val selected=setOf(scenes[1].id,diaryActionKey(action))
        val marked=diaryReplayMarkers(base,selected)
        assertEquals(selected,marked.filter { it.selected }.map { it.id }.toSet())
        assertEquals(base.map { it.id to it.point },marked.map { it.id to it.point })
        assertEquals(scenes[0].id,base.single { it.selected }.id)
        assertTrue(diaryReplayMarkers(marked,emptySet()).none { it.selected })
    }
}
