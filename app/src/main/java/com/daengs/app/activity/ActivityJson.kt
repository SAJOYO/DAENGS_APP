package com.daengs.app.activity

import kotlinx.serialization.json.*
import java.math.BigInteger

/** Optional 값도 키가 빠진 것과 명시적인 null을 구분한다. 새 필드는 허용한다. */
internal object ActivityJson {
    private val json = Json
    private fun root(body: String) = json.parseToJsonElement(body).jsonObject
    private fun JsonObject.field(key: String) = getValue(key)
    private fun JsonElement.text(): String = jsonPrimitive.let {
        require(it.isString); it.content
    }
    private fun JsonElement.integer(): String = jsonPrimitive.let {
        require(!it.isString && it.content.matches(Regex("-?(0|[1-9][0-9]*)")))
        it.content
    }
    private fun JsonObject.text(key: String) = field(key).text()
    private fun JsonObject.number(key: String) = field(key).integer().toLong()
    private fun JsonObject.uuid(key: String) = text(key).also(::requireActivityUuid)
    private fun <T> JsonObject.nullable(key: String, parse: (JsonElement) -> T): T? =
        field(key).let { if (it == JsonNull) null else parse(it) }
    private fun JsonObject.nullableUuid(key: String) = nullable(key) { it.text().also(::requireActivityUuid) }
    private fun JsonObject.nullableLong(key: String) = nullable(key) { it.integer().toLong() }
    private fun JsonObject.optionalLong(key: String) = if (containsKey(key)) nullableLong(key) else null
    private fun <T> JsonObject.items(key: String, parse: (JsonElement) -> T) = field(key).jsonArray.map(parse)

    fun session(body: String): ActivitySessionLink = root(body).run {
        ActivitySessionLink(uuid("client_session_id"), nullableUuid("walk_id"), nullableUuid("game_session_id"),
            ActivityLinkStatus.valueOf(text("status")), items("conflicts") { it.text() })
    }

    fun walk(body: String): ActivityWalkSummary = root(body).run {
        val identity = field("identity").jsonObject
        val versions = field("expected_versions").jsonObject
        val basis = text("window_basis").also { require(it == "walk_end") }
        ActivityWalkSummary(
            ActivityProjectionIdentity(identity.text("generation_id"), identity.text("statistics_version")),
            versions.run { ActivityAnalysisVersions(number("facts"), number("calculation"), number("receipt"), number("capsule")) },
            uuid("owner_id"), nullableUuid("pet_id"), number("from_ms"), number("to_ms"),
            number("recorded_walk_count"), number("observed_walk_count"),
            nullableLong("moving_distance_m"), nullableLong("moving_s"), nullableLong("stop_count"), nullableLong("stop_s"),
            nullable("avg_speed_mps") { value -> value.jsonPrimitive.let {
                require(!it.isString); it.double.also { number -> require(number.isFinite()) }
            } },
            items("exclusions") { value -> value.jsonArray.let {
                require(it.size == 2); ActivityExclusion(it[0].text(), it[1].integer().toLong())
            } },
            items("sources") { it.jsonObject.run { ActivityWalkSource(uuid("walk_id"), uuid("analysis_id"), number("revision")) } },
            basis, number("pending_walk_count"), ActivityWalkStatus.valueOf(text("status")),
        )
    }

    fun territory(body: String): ActivityTerritorySummary = root(body).run {
        ActivityTerritorySummary(text("season_id"), uuid("pet_id"), ActivityTerritoryStatus.valueOf(text("status")),
            number("source_revision"), number("processed_revision"),
            nullable("statistics") { it.jsonObject.run {
                ActivityTerritoryStatistics(text("statistics_version"), text("generation_id"),
                    number("coverage_start_ms"), number("confirmed_through_ms"), number("acquisition_count"),
                    number("takeover_count"), number("held_site_ms"), number("verified_held_site_ms"),
                    number("owned_site_count"), number("peak_owned_site_count"))
            } },
            nullable("score") { it.jsonObject.run {
                ActivityScore(number("bonus"), BigInteger(field("holding_units").integer()), number("held_site_ms"),
                    number("current_count"), number("scoring_count"), number("peak"), number("claims"),
                    number("takeovers"), number("last_ms"), optionalLong("base_bonus"), optionalLong("takeover_bonus"))
                    .also { score ->
                        if (score.baseBonus != null || score.takeoverBonus != null) {
                            require(score.baseBonus != null && score.takeoverBonus != null)
                            require(score.baseBonus >= 0 && score.takeoverBonus >= 0 &&
                                BigInteger.valueOf(score.baseBonus) + BigInteger.valueOf(score.takeoverBonus) == BigInteger.valueOf(score.bonus))
                        }
                    }
            } }, nullableLong("score_as_of_ms"),
            items("sources") { it.jsonObject.run {
                ActivityHoldingSource(uuid("period_id"), text("site_id"), nullableUuid("claim_id"), nullableUuid("game_session_id"))
            } }, optionalLong("final_rank")?.also { require(it > 0) })
    }
}
