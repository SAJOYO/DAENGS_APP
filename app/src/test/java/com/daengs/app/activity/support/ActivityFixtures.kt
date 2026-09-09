package com.daengs.app.activity.support

import com.daengs.app.activity.ActivityWalkWindow

internal object ActivityFixtures {
    const val OWNER = "11111111-1111-1111-1111-111111111111"
    const val PET = "22222222-2222-2222-2222-222222222222"
    const val SESSION = "33333333-3333-3333-3333-333333333333"
    const val WALK = "44444444-4444-4444-4444-444444444444"
    const val ANALYSIS = "55555555-5555-5555-5555-555555555555"
    const val SEASON = "2026 fall+서울"
    val window = ActivityWalkWindow(1000, 2000, PET)
    fun session(status: String = "WALK_ONLY") = """{
      "client_session_id":"$SESSION", "walk_id":"$WALK", "game_session_id":null,
      "status":"$status", "conflicts":[], "future_field":true
    }"""
    fun walk(status: String = "PENDING", pet: String = "\"$PET\"") = """{
      "identity":{"generation_id":"dev-activity.v1","statistics_version":"activity-statistics.v1"},
      "expected_versions":{"facts":1,"calculation":2,"receipt":3,"capsule":4},
      "owner_id":"$OWNER", "pet_id":$pet, "from_ms":1000, "to_ms":2000,
      "recorded_walk_count":1,"observed_walk_count":0,"moving_distance_m":null,"moving_s":null,
      "stop_count":null,"stop_s":null,"avg_speed_mps":null,
      "exclusions":[["no_observed_intervals",1]],
      "sources":[{"walk_id":"$WALK","analysis_id":"$ANALYSIS","revision":7}],
      "window_basis":"walk_end","pending_walk_count":1,"status":"$status"
    }"""
    fun territory(status: String = "STALE") = """{
      "season_id":"$SEASON","pet_id":"$PET","status":"$status",
      "source_revision":9,"processed_revision":8,
      "statistics":{"statistics_version":"activity-statistics.v1","generation_id":"dev-activity.v1",
        "coverage_start_ms":1000,"confirmed_through_ms":2000,"acquisition_count":2,"takeover_count":1,
        "held_site_ms":500,"verified_held_site_ms":200,"owned_site_count":1,"peak_owned_site_count":2},
      "score":{"bonus":100,"holding_units":922337203685477580812345,"held_site_ms":600,
        "current_count":1,"scoring_count":1,"peak":2,"claims":2,"takeovers":1,"last_ms":2500},
      "score_as_of_ms":2500,
      "sources":[{"period_id":"$ANALYSIS","site_id":"territory-site:hex-v1:140:1:2",
        "claim_id":null,"game_session_id":null}]
    }"""
}
