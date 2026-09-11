package com.daengs.app.map.style

/** Display-only cache for immutable point snapshots. The original full painter remains the
 * reference implementation: we reuse it on a small suffix, including one context edge.
 * Prefix validation and the flat result-list copy are still O(n); interpolation is bounded
 * for one-point appends. No chained sublists or mutable outputs are retained across updates.
 */
internal class WalkSpeedPathCache(private val onPaint: ((Int) -> Unit)? = null) {
    private data class Snapshot(val path: List<WalkSpeedPoint>, val policy: WalkStylePolicy,
        val theme: String, val parts: List<WalkSpeedPart>, val lastEdgePartCount: Int)
    private var snapshot: Snapshot? = null

    fun paint(path: List<WalkSpeedPoint>, policy: WalkStylePolicy, theme: String): List<WalkSpeedPart> {
        val previous = snapshot
        val sameStyle = previous != null && previous.policy == policy && previous.theme == theme
        if (sameStyle && previous!!.path == path) return previous.parts
        val append = sameStyle && previous!!.path.size >= 2 && path.size > previous.path.size &&
            path.subList(0, previous.path.size) == previous.path
        val result = if (append) {
            val old = previous!!
            // The old terminal edge's end color depends on the newly added edge's speed.
            val affectedEdge = old.path.size - 2
            val contextStart = (affectedEdge - 1).coerceAtLeast(0)
            val suffix = render(path.subList(contextStart, path.size), policy, theme)
            // This first context edge supplies speed only; its start has an omitted neighbor.
            // Count its parts using the unchanged painter, not a duplicate subdivision policy.
            val contextParts = if (contextStart < affectedEdge)
                render(path.subList(contextStart, contextStart + 2), policy, theme).size else 0
            buildList(old.parts.size + suffix.size) {
                addAll(old.parts.subList(0, old.parts.size - old.lastEdgePartCount))
                addAll(suffix.subList(contextParts, suffix.size))
            }
        } else {
            // Initial load, trim, replacement, corrections and palette changes invalidate all.
            render(path, policy, theme)
        }
        // Neighbor speeds affect colors, never the terminal edge's subdivision count.
        val tailCount = if (path.size >= 2) render(path.takeLast(2), policy, theme).size else 0
        snapshot = Snapshot(path, policy, theme, result, tailCount)
        return result
    }

    private fun render(path: List<WalkSpeedPoint>, policy: WalkStylePolicy, theme: String): List<WalkSpeedPart> {
        onPaint?.invoke(path.size)
        return paintWalkSpeedPath(path, policy, theme)
    }
}
