package com.daengs.app.walk.diary

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val SPATIAL_DIARY_VIEW_VERSION = 1
const val SPATIAL_DIARY_CONTEXT_POLICY_VERSION = 2
const val SPATIAL_DIARY_AGGREGATION_VERSION = 1
const val SPATIAL_DIARY_GRID_VERSION = "hex-v1"

enum class SpatialDiaryMetric(val wireName: String) {
    VISIT_RATE("visit_rate"),
    WALK_UTILIZATION("walk_utilization"),
    ;

    companion object {
        fun fromWire(value: String): SpatialDiaryMetric = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("지원하지 않는 공간 일기 지표예요: $value")
    }
}

enum class SpatialDiaryPrecipitation(val wireName: String) {
    RAIN("rain"),
    SNOW("snow"),
    MIXED("mixed"),
    DRY("dry"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWire(value: String): SpatialDiaryPrecipitation = entries.singleOrNull {
            it.wireName == value
        } ?: throw IllegalArgumentException("지원하지 않는 강수 필터예요: $value")
    }
}

enum class SpatialDiaryDaylight(val wireName: String) {
    DAY("day"),
    NIGHT("night"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWire(value: String): SpatialDiaryDaylight = entries.singleOrNull {
            it.wireName == value
        } ?: throw IllegalArgumentException("지원하지 않는 낮·밤 필터예요: $value")
    }
}

/** 서로 다른 축은 AND, 같은 축 안의 값은 OR로 서버가 조립한다. */
sealed interface SpatialDiaryContextFilter {
    val axis: String
    val wireValues: List<String>

    fun toJson(): JSONObject = JSONObject().apply {
        put("axis", axis)
        put("values", JSONArray(wireValues.sorted()))
        put("policy_version", SPATIAL_DIARY_CONTEXT_POLICY_VERSION)
    }

    companion object {
        fun parse(json: JSONObject): SpatialDiaryContextFilter {
            require(
                json.getInt("policy_version") == SPATIAL_DIARY_CONTEXT_POLICY_VERSION,
            ) { "지원하지 않는 공간 일기 환경 정책이에요." }
            val rawValues = json.getJSONArray("values").strings()
            require(rawValues.isNotEmpty() && rawValues.size == rawValues.toSet().size) {
                "공간 일기 환경 필터 값은 비어 있거나 중복될 수 없어요."
            }
            return when (val axis = json.getString("axis")) {
                PRECIPITATION_AXIS -> PrecipitationFilter(
                    rawValues.map(SpatialDiaryPrecipitation::fromWire).toSet(),
                )
                DAYLIGHT_AXIS -> DaylightFilter(
                    rawValues.map(SpatialDiaryDaylight::fromWire).toSet(),
                )
                else -> throw IllegalArgumentException("지원하지 않는 공간 일기 환경 축이에요: $axis")
            }
        }
    }
}

data class PrecipitationFilter(
    val values: Set<SpatialDiaryPrecipitation>,
) : SpatialDiaryContextFilter {
    init {
        require(values.isNotEmpty()) { "강수 필터는 하나 이상의 값을 가져야 해요." }
    }

    override val axis: String = PRECIPITATION_AXIS
    override val wireValues: List<String> get() = values.map { it.wireName }
}

data class DaylightFilter(
    val values: Set<SpatialDiaryDaylight>,
) : SpatialDiaryContextFilter {
    init {
        require(values.isNotEmpty()) { "낮·밤 필터는 하나 이상의 값을 가져야 해요." }
    }

    override val axis: String = DAYLIGHT_AXIS
    override val wireValues: List<String> get() = values.map { it.wireName }
}

data class SpatialDiaryQuery(
    val petId: String,
    val since: LocalDate? = null,
    val until: LocalDate? = null,
    val contextFilters: List<SpatialDiaryContextFilter> = emptyList(),
    val metric: SpatialDiaryMetric = SpatialDiaryMetric.VISIT_RATE,
) {
    init {
        require(petId.isNotBlank()) { "공간 일기를 볼 강아지가 필요해요." }
        if (since != null && until != null) {
            require(!since.isAfter(until)) { "공간 일기 시작일이 종료일보다 늦을 수 없어요." }
            require(ChronoUnit.DAYS.between(since, until) + 1 <= MAX_QUERY_DAYS) {
                "공간 일기는 한 번에 최대 366일까지 볼 수 있어요."
            }
        }
        require(contextFilters.map { it.axis }.distinct().size == contextFilters.size) {
            "공간 일기 환경 축은 한 번씩만 고를 수 있어요."
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("view_version", SPATIAL_DIARY_VIEW_VERSION)
        put(
            "walk_selector",
            JSONObject().apply {
                put("pet_id", petId)
                since?.let { put("since", it.toString()) }
                until?.let { put("until", it.toString()) }
                put(
                    "context_facets",
                    JSONArray().also { array ->
                        contextFilters.sortedBy { it.axis }.forEach { array.put(it.toJson()) }
                    },
                )
            },
        )
        put("field_metric", metric.wireName)
    }

    companion object {
        fun parse(json: JSONObject): SpatialDiaryQuery {
            require(json.getInt("view_version") == SPATIAL_DIARY_VIEW_VERSION) {
                "지원하지 않는 공간 일기 화면 버전이에요."
            }
            val selector = json.getJSONObject("walk_selector")
            val facets = selector.optJSONArray("context_facets") ?: JSONArray()
            return SpatialDiaryQuery(
                petId = selector.getString("pet_id"),
                since = selector.optNonBlank("since")?.let(LocalDate::parse),
                until = selector.optNonBlank("until")?.let(LocalDate::parse),
                contextFilters = (0 until facets.length()).map {
                    SpatialDiaryContextFilter.parse(facets.getJSONObject(it))
                }.sortedBy { it.axis },
                metric = SpatialDiaryMetric.fromWire(json.getString("field_metric")),
            )
        }
    }
}

data class SpatialDiaryProjection(
    val paintVersion: Int,
    val gridVersion: String,
    val radiusU: Double,
    val profileName: String,
    val profileFingerprint: String,
    val sampleStepMeters: Double,
    val paintFingerprint: String,
) {
    init {
        require(paintVersion > 0)
        require(gridVersion == SPATIAL_DIARY_GRID_VERSION) {
            "지원하지 않는 공간 일기 격자예요: $gridVersion"
        }
        require(radiusU.isFinite() && radiusU > 0)
        require(sampleStepMeters.isFinite() && sampleStepMeters > 0)
        require(profileName.isNotBlank() && profileFingerprint.isNotBlank() && paintFingerprint.isNotBlank())
    }

    companion object {
        fun parse(json: JSONObject) = SpatialDiaryProjection(
            paintVersion = json.getInt("paint_version"),
            gridVersion = json.getString("grid_version"),
            radiusU = json.getDouble("radius_u"),
            profileName = json.getString("profile_name"),
            profileFingerprint = json.getString("profile_fp"),
            sampleStepMeters = json.getDouble("sample_step_m"),
            paintFingerprint = json.getString("paint_fp"),
        )
    }
}

data class SpatialDiaryCell(
    val q: Int,
    val r: Int,
    val value: Double,
    val numerator: Double,
) {
    init {
        require(value.isFinite() && value >= 0)
        require(numerator.isFinite() && numerator >= 0)
    }

    companion object {
        fun parse(json: JSONObject) = SpatialDiaryCell(
            q = json.getInt("q"),
            r = json.getInt("r"),
            value = json.getDouble("value"),
            numerator = json.getDouble("numerator"),
        )
    }
}

data class SpatialDiaryField(
    val metric: SpatialDiaryMetric,
    val unit: String,
    val normalization: String,
    val denominator: Double,
    val cells: List<SpatialDiaryCell>,
) {
    init {
        require(unit.isNotBlank() && normalization.isNotBlank())
        require(denominator.isFinite() && denominator >= 0)
        require(cells.map { it.q to it.r }.distinct().size == cells.size) {
            "공간 일기 응답에 같은 셀이 두 번 들어왔어요."
        }
    }

    companion object {
        fun parse(json: JSONObject): SpatialDiaryField {
            val cells = json.getJSONArray("cells")
            return SpatialDiaryField(
                metric = SpatialDiaryMetric.fromWire(json.getString("metric")),
                unit = json.getString("unit"),
                normalization = json.getString("normalization"),
                denominator = json.getDouble("denominator"),
                cells = (0 until cells.length()).map { SpatialDiaryCell.parse(cells.getJSONObject(it)) },
            )
        }
    }
}

data class SpatialDiaryReceipt(
    val selectorFingerprint: String,
    val viewAsOf: Instant,
    val totalCapsules: Int,
    val selectedCapsules: Int,
    val contributingCapsules: Int,
    val contextKnownCount: Int,
    val contextUnknownCount: Int,
    val paintFingerprint: String,
    val fieldMetric: SpatialDiaryMetric,
    val normalization: String,
    val contextPolicyVersion: Int,
    val aggregationVersion: Int,
) {
    init {
        require(totalCapsules >= selectedCapsules && selectedCapsules >= contributingCapsules)
        require(contributingCapsules >= 0)
        require(contextKnownCount >= 0 && contextUnknownCount >= 0)
        require(contextKnownCount + contextUnknownCount == selectedCapsules)
        require(contextPolicyVersion == SPATIAL_DIARY_CONTEXT_POLICY_VERSION)
        require(aggregationVersion == SPATIAL_DIARY_AGGREGATION_VERSION)
        require(selectorFingerprint.isNotBlank() && paintFingerprint.isNotBlank() && normalization.isNotBlank())
    }

    companion object {
        fun parse(json: JSONObject) = SpatialDiaryReceipt(
            selectorFingerprint = json.getString("selector_fingerprint"),
            viewAsOf = Instant.parse(json.getString("view_as_of")),
            totalCapsules = json.getInt("total_capsules"),
            selectedCapsules = json.getInt("selected_capsules"),
            contributingCapsules = json.getInt("contributing_capsules"),
            contextKnownCount = json.getInt("context_known_count"),
            contextUnknownCount = json.getInt("context_unknown_count"),
            paintFingerprint = json.getString("paint_fp"),
            fieldMetric = SpatialDiaryMetric.fromWire(json.getString("field_metric")),
            normalization = json.getString("normalization"),
            contextPolicyVersion = json.getInt("context_policy_version"),
            aggregationVersion = json.getInt("aggregation_version"),
        )
    }
}

data class SpatialDiaryView(
    val query: SpatialDiaryQuery,
    val projection: SpatialDiaryProjection,
    val field: SpatialDiaryField,
    val receipt: SpatialDiaryReceipt,
) {
    init {
        require(query.metric == field.metric && field.metric == receipt.fieldMetric)
        require(field.normalization == receipt.normalization)
        require(projection.paintFingerprint == receipt.paintFingerprint)
        val receiptDenominator = when (field.metric) {
            SpatialDiaryMetric.VISIT_RATE -> receipt.selectedCapsules
            SpatialDiaryMetric.WALK_UTILIZATION -> receipt.contributingCapsules
        }
        require(field.denominator == receiptDenominator.toDouble()) {
            "공간 일기 field와 영수증의 분모가 달라요."
        }
    }

    companion object {
        fun parse(json: JSONObject) = SpatialDiaryView(
            query = SpatialDiaryQuery.parse(json.getJSONObject("spec")),
            projection = SpatialDiaryProjection.parse(json.getJSONObject("projection")),
            field = SpatialDiaryField.parse(json.getJSONObject("field")),
            receipt = SpatialDiaryReceipt.parse(json.getJSONObject("receipt")),
        )
    }
}

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONObject.optNonBlank(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private const val PRECIPITATION_AXIS = "precipitation"
private const val DAYLIGHT_AXIS = "daylight"
private const val MAX_QUERY_DAYS = 366L
