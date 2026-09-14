package com.daengs.app.ui.walk

import com.daengs.app.walk.diary.DiarySlotEvidence
import org.junit.Assert.*
import org.junit.Test

class DiarySlotEvidenceTextTest {
    @Test fun `normalized commerce retains composition distribution and query relation`() {
        val text = slotEvidenceDescription(DiarySlotEvidence("test", "space", "scene_area_context", """
            {"format":"space-material-v1","material":{"업종구성":"음식점·카페 중심","분포":"등록 상가가 모인 구간"},
             "relation":{"kind":"registered_distribution_in_query_circle","radius_m":1000,"nearest_registered_point_m":349.283}}
        """))
        assertTrue(text.contains("음식점·카페 중심"))
        assertTrue(text.contains("등록 상가가 모인 구간"))
        assertTrue(text.contains("분포를 확인한 반경: 1000m"))
        assertTrue(text.contains("가장 가까운 등록 업소까지: 349.283m"))
        assertFalse(text.contains("분포 중심까지"))
    }

    @Test fun `park point relation and land classification remain different meanings`() {
        val park = slotEvidenceDescription(DiarySlotEvidence("park", "space", "scene_registered_point_distance", """
            {"format":"space-material-v1","material":{"공원명":"청룡공원","공원종류":"근린공원"},
             "relation":{"kind":"registered_park_point_distance","distance_m":42}}
        """))
        assertTrue(park.contains("공원명: 청룡공원"))
        assertTrue(park.contains("공원 등록 지점까지: 42m"))
        assertFalse(park.contains("공원 내부"))
        val land = slotEvidenceDescription(DiarySlotEvidence("land", "space", "scene_geometry_distance", """
            {"format":"space-material-v1","material":{"피복":"상업·업무시설"},
             "relation":{"kind":"land_cover_at_query_point","distance_m":0}}
        """))
        assertTrue(land.contains("피복: 상업·업무시설"))
        assertTrue(land.contains("장면 좌표의 피복"))
        assertFalse(land.contains("거리"))
    }

    @Test fun `missing or null temperature is never displayed as zero or current weather`() {
        for (facts in listOf("{}", "{\"temperature_c\":null,\"observed_at\":null,\"grid\":null}")) {
            val text = slotEvidenceDescription(DiarySlotEvidence("test", "environment", "grid_temperature_observation", facts))
            assertFalse(text.contains("기온 (°C):"))
            assertFalse(text.contains("관측 시각:"))
            assertFalse(text.contains("격자 (nx, ny):"))
        }
    }

    @Test fun `zero degrees and zero observation age are valid supplied facts`() {
        val text = slotEvidenceDescription(DiarySlotEvidence("test", "environment", "grid_temperature_observation",
            "{\"temperature_c\":0,\"observation_age_s\":0,\"observed_at\":\"2026-09-11T17:00:00+09:00\"}"))
        assertTrue(text.contains("기온 (°C): 0"))
        assertTrue(text.contains("기록보다 앞선 시간 (초): 0"))
        assertTrue(text.contains("관측 시각: 2026-09-11T17:00:00+09:00"))
        assertFalse(text.contains("풍속"))
        assertFalse(text.contains("격자"))
    }
}
