package com.daengs.app.walk.support

import org.json.JSONObject

/** Shared with the server contract fixture; each test owns its mutable JSON copy. */
internal fun behaviorComparisonFixture(): JSONObject = JSONObject(
    BehaviorComparisonResources::class.java.getResource("/walk_behavior_comparison_response.json")!!.readText())

private object BehaviorComparisonResources
