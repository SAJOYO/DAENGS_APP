package com.daengs.app.map.layers.traces

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.diary.SpatialDiaryCellId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Display-only reconstruction. Never use its smoothed coverage as an observation count. */
object TraceBrush {
    fun mask(
        sheet: WalkTraceSheet, policy: TraceBrushPolicy = TraceBrushPolicy(),
        checkCancelled: () -> Unit = {},
    ): WalkTraceMask {
        checkCancelled()
        require(sheet.cells.size <= policy.maxCells) { "한 산책의 표시 셀이 너무 많아요." }
        require(policy.pixelU <= sheet.radiusU && policy.insetU <= sheet.radiusU / 2) {
            "셀 크기에 비해 브러시 픽셀이나 안쪽 축소 폭이 너무 커요."
        }
        val span = policy.pixelU * policy.tileSize
        val halo = ceil(3 * policy.sigmaU / policy.pixelU).toInt() +
            ceil(policy.insetU / policy.pixelU).toInt() + 2
        var rasterTileCount = 0
        val uniqueTiles = mutableSetOf<TileKey>()
        val groups = components(sheet.cells.toSet(), checkCancelled).map { component ->
            checkCancelled()
            val bins = linkedMapOf<TileKey, MutableList<Center>>()
            component.forEach { cell ->
                checkCancelled()
                val center = Center(
                    sheet.radiusU * sqrt(3.0) * (cell.q + cell.r / 2.0),
                    sheet.radiusU * 1.5 * cell.r,
                )
                require(abs(center.x) + sheet.radiusU < WORLD_EDGE &&
                    abs(center.y) + sheet.radiusU < WORLD_EDGE) { "표시할 수 없는 지도 좌표예요." }
                val reach = sheet.radiusU + halo * policy.pixelU
                val west = floor((center.x - reach) / span).toInt()
                val east = floor((center.x + reach) / span).toInt()
                val south = floor((center.y - reach) / span).toInt()
                val north = floor((center.y + reach) / span).toInt()
                for (x in west..east) for (y in south..north) {
                    val key = TileKey(x, y)
                    bins.getOrPut(key) {
                        rasterTileCount++
                        uniqueTiles.add(key)
                        require(rasterTileCount <= policy.maxRasterTiles) {
                            "산책 흔적이 너무 넓게 흩어져 있어요. 산책을 줄여 주세요."
                        }
                        require(uniqueTiles.size <= policy.maxTiles) {
                            "표시할 지도 타일이 너무 많아요. 산책을 줄여 주세요."
                        }
                        mutableListOf()
                    }.add(center)
                }
            }
            bins
        }
        val result = linkedMapOf<TileKey, FloatArray>()
        val kernel = gaussianKernel(policy)
        groups.forEach { bins ->
            // Disconnected paths are blurred separately. Their tails must not add into a bridge.
            var rendered = bins.mapValues { (key, centers) ->
                rasterTile(key, centers, sheet.radiusU, policy, halo, kernel, checkCancelled)
            }
            // Decide for the entire island, not a tile: per-tile fallbacks cause visible seams.
            if (rendered.values.none { pixels -> pixels.any { it > 0 } }) {
                val fallback = policy.copy(insetU = 0.0, sigmaU = min(policy.sigmaU, sheet.radiusU / 2))
                val fallbackKernel = gaussianKernel(fallback)
                rendered = bins.mapValues { (key, centers) ->
                    rasterTile(key, centers, sheet.radiusU, fallback, halo, fallbackKernel, checkCancelled)
                }
            }
            rendered.forEach { (key, pixels) ->
                if (pixels.any { it > 0 }) {
                    val target = result.getOrPut(key) { FloatArray(policy.tileSize * policy.tileSize) }
                    pixels.indices.forEach { target[it] = max(target[it], pixels[it]) }
                }
            }
        }
        return WalkTraceMask(sheet.walkId, result.entries.sortedWith(
            compareBy({ it.key.y }, { it.key.x }),
        ).map { (key, pixels) -> tile(key, policy.tileSize, policy.pixelU, pixels) })
    }

    /** Same-colour source-over capped at 40%; individual mask coverage remains untouched. */
    fun compose(
        masks: List<WalkTraceMask>, baseAlpha: Double = DEFAULT_TRACE_BASE_ALPHA, checkCancelled: () -> Unit = {},
    ): List<TraceRasterTile> {
        checkCancelled()
        require(baseAlpha.isFinite() && baseAlpha in 0.0..1.0)
        require(masks.size <= 400 && masks.map { it.walkId }.distinct().size == masks.size) {
            "같은 산책을 두 장으로 겹칠 수 없어요."
        }
        require(masks.sumOf { it.tiles.size } <= 1_024) { "합칠 산책 타일이 너무 많아요." }
        val first = masks.firstNotNullOfOrNull { it.tiles.firstOrNull() } ?: return emptyList()
        require(first.size in 32..256 && first.pixelU.isFinite() && first.pixelU in 0.25..16.0)
        val result = linkedMapOf<TileKey, FloatArray>()
        masks.sortedBy { it.walkId }.forEach { mask ->
            require(mask.walkId.isNotBlank())
            require(mask.tiles.map { it.tileX to it.tileY }.distinct().size == mask.tiles.size) {
                "한 산책에 같은 타일이 두 번 들어왔어요."
            }
            mask.tiles.forEach { source ->
                checkCancelled()
                require(source.size == first.size && source.pixelU == first.pixelU) {
                    "같은 표시 격자의 산책만 겹칠 수 있어요."
                }
                require(source.alpha.size == source.size * source.size)
                val key = TileKey(source.tileX, source.tileY)
                require(result.containsKey(key) || result.size < 256) { "합칠 지도 범위가 너무 넓어요." }
                val target = result.getOrPut(key) { FloatArray(source.alpha.size) }
                source.alpha.indices.forEach { i ->
                    if (i % 4_096 == 0) checkCancelled()
                    val coverage = source.alpha[i]
                    require(coverage.isFinite() && coverage in 0f..1f)
                    val alpha = coverage * baseAlpha
                    target[i] = min(MAX_TRACE_COMPOSITE_ALPHA, 1 - (1 - target[i]) * (1 - alpha)).toFloat()
                }
            }
        }
        return result.entries.sortedWith(compareBy({ it.key.y }, { it.key.x })).map { (key, pixels) ->
            tile(key, first.size, first.pixelU, pixels)
        }
    }

    private fun rasterTile(
        key: TileKey, centers: List<Center>, radius: Double, policy: TraceBrushPolicy,
        halo: Int, kernel: DoubleArray, checkCancelled: () -> Unit,
    ): FloatArray {
        checkCancelled()
        val size = policy.tileSize + 2 * halo
        val step = policy.pixelU
        val left = key.x * policy.tileSize * step - halo * step
        val top = (key.y + 1) * policy.tileSize * step + halo * step
        val support = FloatArray(size * size)
        centers.forEach { center ->
            checkCancelled()
            val x0 = floor((center.x - radius - left) / step).toInt().coerceIn(0, size - 1)
            val x1 = ceil((center.x + radius - left) / step).toInt().coerceIn(0, size - 1)
            val y0 = floor((top - center.y - radius) / step).toInt().coerceIn(0, size - 1)
            val y1 = ceil((top - center.y + radius) / step).toInt().coerceIn(0, size - 1)
            for (row in y0..y1) for (col in x0..x1) {
                val dx = abs(left + (col + 0.5) * step - center.x)
                val dy = abs(top - (row + 0.5) * step - center.y)
                if (dx <= sqrt(3.0) * radius / 2 && dy + dx / sqrt(3.0) <= radius) {
                    support[row * size + col] = 1f
                }
            }
        }
        val inset = erode(support, size, policy.insetU / step, checkCancelled)
        val blurred = blur(inset, size, kernel, checkCancelled)
        return FloatArray(policy.tileSize * policy.tileSize) { index ->
            val row = index / policy.tileSize + halo
            val col = index % policy.tileSize + halo
            // Saturate the narrow trace's interior at 0.5 instead of the Geo experiment's 0.9.
            // Keeping the 0.12 zero cutoff preserves nonzero support: this cannot add a bridge.
            val t = ((blurred[row * size + col] - 0.12f) / 0.38f).coerceIn(0f, 1f)
            t * t * (3 - 2 * t)
        }
    }

    private fun erode(source: FloatArray, size: Int, radius: Double, checkCancelled: () -> Unit): FloatArray {
        if (radius < 1) return source
        val reach = ceil(radius).toInt()
        val offsets = (-reach..reach).flatMap { dy ->
            (-reach..reach).mapNotNull { dx ->
                if (dx * dx + dy * dy <= radius * radius) dx to dy else null
            }
        }
        val result = FloatArray(source.size)
        for (row in reach until size - reach) for (col in reach until size - reach) {
            if (col == reach && row % 16 == 0) checkCancelled()
            if (source[row * size + col] > 0 && offsets.all { (dx, dy) ->
                    source[(row + dy) * size + col + dx] > 0
                }) result[row * size + col] = 1f
        }
        return result
    }

    private fun gaussianKernel(policy: TraceBrushPolicy): DoubleArray {
        if (policy.sigmaU == 0.0) return doubleArrayOf(1.0)
        val sigma = policy.sigmaU / policy.pixelU
        val reach = ceil(3 * sigma).toInt()
        val kernel = DoubleArray(2 * reach + 1) { exp(-((it - reach).toDouble() / sigma).let { x -> x * x } / 2) }
        val sum = kernel.sum()
        return kernel.map { it / sum }.toDoubleArray()
    }

    private fun blur(source: FloatArray, size: Int, kernel: DoubleArray, checkCancelled: () -> Unit): FloatArray {
        val reach = kernel.size / 2
        val horizontal = FloatArray(source.size)
        val result = FloatArray(source.size)
        for (row in 0 until size) for (col in 0 until size) {
            if (col == 0 && row % 16 == 0) checkCancelled()
            var value = 0.0
            for (k in kernel.indices) {
                val x = col + k - reach
                if (x in 0 until size) value += source[row * size + x] * kernel[k]
            }
            horizontal[row * size + col] = value.toFloat()
        }
        for (row in 0 until size) for (col in 0 until size) {
            if (col == 0 && row % 16 == 0) checkCancelled()
            var value = 0.0
            for (k in kernel.indices) {
                val y = row + k - reach
                if (y in 0 until size) value += horizontal[y * size + col] * kernel[k]
            }
            result[row * size + col] = value.toFloat()
        }
        return result
    }

    private fun components(cells: Set<SpatialDiaryCellId>, checkCancelled: () -> Unit): List<Set<SpatialDiaryCellId>> {
        val remaining = cells.toMutableSet()
        val result = mutableListOf<Set<SpatialDiaryCellId>>()
        while (remaining.isNotEmpty()) {
            val group = linkedSetOf<SpatialDiaryCellId>()
            val queue = ArrayDeque<SpatialDiaryCellId>()
            queue.add(remaining.first().also { remaining.remove(it) })
            while (queue.isNotEmpty()) {
                if (group.size % 256 == 0) checkCancelled()
                val cell = queue.removeFirst()
                group.add(cell)
                for ((dq, dr) in NEIGHBOURS) {
                    val neighbour = SpatialDiaryCellId(cell.q + dq, cell.r + dr)
                    if (remaining.remove(neighbour)) queue.add(neighbour)
                }
            }
            result.add(group)
        }
        return result
    }

    private fun tile(key: TileKey, size: Int, step: Double, alpha: FloatArray): TraceRasterTile {
        val span = size * step
        return TraceRasterTile(key.x, key.y, size, step, alpha,
            point(key.x * span, key.y * span), point((key.x + 1) * span, (key.y + 1) * span))
    }

    private fun point(x: Double, y: Double) = GeoPoint(
        Math.toDegrees(2 * atan(exp(y / EARTH_RADIUS)) - PI / 2), Math.toDegrees(x / EARTH_RADIUS),
    )

    private data class TileKey(val x: Int, val y: Int)
    private data class Center(val x: Double, val y: Double)
    private val NEIGHBOURS = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)
    private const val EARTH_RADIUS = 6_378_137.0
    private const val WORLD_EDGE = PI * EARTH_RADIUS
}
