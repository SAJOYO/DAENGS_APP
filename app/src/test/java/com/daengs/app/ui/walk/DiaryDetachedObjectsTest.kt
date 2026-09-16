package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.*
import com.daengs.app.map.shell.MapScene
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import org.junit.Assert.*
import org.junit.Test

class DiaryDetachedObjectsTest {
    @Test fun `boundary identification never uses title ordinal or proximity`() {
        val ordinary=DiaryScene("s/scene-1","s",0,"산책 시작","body",null,"")
        assertFalse(ordinary.isWalkBoundary())
        assertTrue(ordinary.copy(id="s/start",title="edited").isWalkBoundary())
        assertTrue(ordinary.copy(id="s/geo:end").isWalkBoundary())
        assertFalse(ordinary.copy(id="s/start",entryId="entry").isWalkBoundary())
    }
    @Test fun `fixed same point start end are combined without a floating scene`() {
        val p=GeoPoint(37.5,127.0)
        val scene=MapScene(completedRoute=CompletedRouteLayerState(
            start=RouteEndpointMarkerState("start",p,"출발",RouteEndpointKind.START),
            end=RouteEndpointMarkerState("end",p,"도착",RouteEndpointKind.END)),detachedDiaryPins=true)
        val endpoint=scene.routeEndpointStamps().single()
        assertEquals("산책 시작·끝",endpoint.label); assertTrue(endpoint.abovePoint)
        assertEquals(p,endpoint.point)
    }
    @Test fun `actions preserve separate source identities and omit unlocated or other session records`() {
        val p=GeoPoint(37.5,127.0)
        val a=WalkEntry("a","s",WalkMomentType.SNIFFING,1000,p,1000)
        val b=a.copy(id="b",type=WalkMomentType.BARKING)
        val markers=diaryActionObjects(listOf(a,b,a.copy(id="empty",point=null,locationCapturedAtMillis=null),a.copy(id="foreign",sessionId="other")),"s",setOf(diaryActionKey(b)))
        assertEquals(2,markers.size); assertEquals(2,markers.map { it.id }.distinct().size)
        assertFalse(markers[0].selected); assertTrue(markers[1].selected)
        assertTrue(markers.all { it.diaryPin==null && it.recordPin?.count==1 })
    }
    @Test fun `v2 unlocated pin does not resurrect the original GPS coordinate`() {
        val entry = WalkEntry("a", "s", WalkMomentType.SNIFFING, 1000, GeoPoint(37.5,127.0), 1000,
            pin = com.daengs.app.walk.pin.ActionPin("""{"point":null,"state":"unlocated","method":"none"}"""))
        assertTrue(diaryActionObjects(listOf(entry), "s", emptySet()).isEmpty())
        val resolved = entry.copy(pin = com.daengs.app.walk.pin.ActionPin(
            """{"point":{"lat":37.6,"lng":127.1},"state":"resolved","method":"observed"}"""))
        assertEquals(GeoPoint(37.6,127.1), diaryActionObjects(listOf(resolved), "s", emptySet()).single().point)
    }
}
