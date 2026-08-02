package storage

import dev.kord.common.entity.Snowflake
import dev.kord.common.serialization.InstantInEpochMillisecondsSerializer
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.i18n.Key
import dev.kordex.core.time.TimestampType
import extensions.ModifySanctionValues
import fr.ayfri.rocketmanager.i18n.Translations
import kotlin.time.Clock
import kotlin.time.toKotlinInstant
import kotlinx.serialization.Serializable
import java.sql.ResultSet
import java.sql.Timestamp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.toDuration

enum class SanctionType(val translation: Key, val emote: String) : ChoiceEnum {
	BAN(Translations.SanctionTypes.ban, "<:ban:498482002601705482>"),
	KICK(Translations.SanctionTypes.kick, "<:kick:933505066273501184>"),
	MUTE(Translations.SanctionTypes.mute, "<:mute:933505777354834021>"),
	WARN(Translations.SanctionTypes.warn, "⚠️"),
	LIGHT_WARN(Translations.SanctionTypes.lightWarn, "❕");

	override val readableName = translation
}


@Serializable
data class Sanction(
	var type: SanctionType,
	var reason: String = DEFAULT_REASON,
	val member: Snowflake,
	val id: Int = 0,
	val appliedBy: Snowflake? = null,
	var durationMS: Long = 0,
	@Serializable(with = InstantInEpochMillisecondsSerializer::class)
	val sanctionedAt: kotlin.time.Instant = Clock.System.now(),
) {
	constructor(
		type: SanctionType,
		reason: String? = null,
		member: Snowflake,
		appliedBy: Snowflake? = null,
		durationMS: Long = 0
	) : this(
		type, reason ?: DEFAULT_REASON, member, appliedBy = appliedBy, durationMS = durationMS
	)

	val duration
		get() = durationMS.toDuration(DurationUnit.MILLISECONDS)

	fun toDiscordTimestamp(type: TimestampType) = type.format(durationMS)

	val isActive get() = durationMS > 0 && activeUntil > Clock.System.now()

	val activeUntil get() = sanctionedAt + duration

	val formattedDuration: String
		get() = when {
			duration.toDouble(DurationUnit.MILLISECONDS) == 0.0 -> ""
			duration.toDouble(DurationUnit.HOURS) > 24 -> " ${duration.toDouble(DurationUnit.DAYS).roundToInt()}d"
			duration.toDouble(DurationUnit.HOURS) < 1 -> " ${duration.toDouble(DurationUnit.MINUTES).roundToInt()}m"
			else -> " ${duration.toDouble(DurationUnit.HOURS).roundToInt()}h"
		}

	fun equalExceptOwner(other: Sanction) =
		type == other.type &&
			reason == other.reason &&
			member == other.member &&
			abs(durationMS - other.durationMS) < 10_000


	fun save() = saveSanction(type, reason, member, appliedBy, durationMS)
	fun toString(prefix: String) = "$prefix${type.name.lowercase()} <@$member> $reason$formattedDuration"

	companion object {
		const val DEFAULT_REASON = "Pas de raison définie."
	}
}

private fun ResultSet.toSanction() = Sanction(
	type = SanctionType.valueOf(getString("type").uppercase()),
	reason = getString("reason"),
	member = Snowflake(getString("memberID")),
	id = getInt("id"),
	appliedBy = getString("appliedByID")?.takeIf { it != "null" }?.let(::Snowflake),
	durationMS = getLong("durationMS"),
	sanctionedAt = getTimestamp("sanctionedAt").toInstant().toKotlinInstant()
)

fun getSanction(id: Int) = sqlQuery("SELECT * FROM sanctions WHERE id = ?", id) { result ->
	result.takeIf { it.next() }?.toSanction()
}

fun getSanctions(user: Snowflake) = sqlQuery(
	"SELECT * FROM sanctions WHERE memberID = ?",
	user.toString()
) { it.mapRows(ResultSet::toSanction) }

fun getSanctionCount() = sqlQuery("SELECT appliedByID FROM sanctions ORDER BY id") { result ->
	result.mapRows { it.getString("appliedByID") }
}.mapNotNull { it?.takeIf { id -> id != "null" }?.let(::Snowflake) }

/** [newValue] is untyped because `durationMS` is an integer column and the tables are STRICT. */
fun modifySanction(id: Int, value: ModifySanctionValues, newValue: Any?) =
	sqlUpdate("UPDATE sanctions SET ${value.column} = ? WHERE id = ?", newValue, id)

fun removeSanction(id: Int) = sqlUpdate("DELETE FROM sanctions WHERE id = ?", id)

fun removeSanctions(user: Snowflake, type: String? = null) = when (type) {
	null -> sqlUpdate("DELETE FROM sanctions WHERE memberID = ?", user.toString())
	else -> sqlUpdate("DELETE FROM sanctions WHERE memberID = ? AND type = ?", user.toString(), type.lowercase())
}

fun saveSanction(
	type: SanctionType,
	reason: String,
	member: Snowflake,
	appliedBy: Snowflake? = null,
	durationMS: Long? = null
) = sqlUpdate(
	"""
	INSERT INTO sanctions (reason, memberID, appliedByID, durationMS, type, sanctionedAt)
	VALUES (?, ?, ?, ?, ?, ?)
	""".trimIndent(),
	reason,
	member.toString(),
	appliedBy?.toString(),
	durationMS ?: 0L,
	type.name.lowercase(),
	Timestamp.from(java.time.Instant.now())
)
