package com.daengs.app.map.features.territory

/** Map visibility only. The 60/70m gate never changes claim or photo eligibility. */
internal class TerritoryRangeTracker {
    private var visibleIds: Set<String> = emptySet()

    fun update(
        game: TerritoryGameState,
        nearby: TerritoryNearbyState,
        location: TerritoryLocationEvidence,
        visible: Boolean,
    ): Set<String> {
        val sample = location.sample
        val accuracy = sample?.accuracyMeters
        if (!visible || !game.enabled || game.phase == TerritoryWalkPhase.PAUSED ||
            !location.trusted || sample == null || accuracy == null || !accuracy.isFinite() || accuracy < 0) {
            visibleIds = emptySet()
            return visibleIds
        }
        val candidates = nearby.sites.mapTo(hashSetOf()) { it.id }
        visibleIds = game.sites.asSequence().filter { it.site.id in candidates }.filter { site ->
            val distance = sample.point.distanceMetersTo(site.site.point)
            distance.isFinite() && distance <= if (site.site.id in visibleIds) EXIT_METERS else ENTER_METERS
        }.map { it.site.id }.toSet()
        return visibleIds
    }

    private companion object {
        const val ENTER_METERS = 60.0
        const val EXIT_METERS = 70.0
    }
}
