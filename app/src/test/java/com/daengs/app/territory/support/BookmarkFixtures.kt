package com.daengs.app.territory.support

internal const val BOOKMARK_SITE = "territory-site:hex-v1:140:324:777"
internal fun bookmarkJson() = """{"total_count":1,"limit":20,"items":[{"site_id":"$BOOKMARK_SITE",
    "created_at":"2026-09-10T03:00:00Z","location":{"lat":37.5,"lng":127.0},"location_status":"AVAILABLE"}]}"""
internal fun mutationJson(saved: Boolean) = """{"site_id":"$BOOKMARK_SITE","is_bookmarked":$saved,
    "created_at":${if (saved) "\"2026-09-10T03:00:00Z\"" else "null"},"total_count":${if(saved) 1 else 0},"limit":20}"""
