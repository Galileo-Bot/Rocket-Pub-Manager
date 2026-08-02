package storage

import dev.kord.common.entity.Snowflake
import dev.kord.common.serialization.InstantInEpochMillisecondsSerializer
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.i18n.Key
import dev.kordex.core.time.TimestampType
import fr.ayfri.rocketmanager.i18n.Translations
import kotlin.time.Clock
import kotlin.time.toKotlinInstant
import kotlinx.serialization.Serializable
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
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

	/** The `type` column holds the lowercase name, every query filtering on it must use this form. */
	val storedName get() = name.lowercase()
}

/** The columns of `sanctions` an existing row may be edited on. */
enum class ModifySanctionValues(val column: String, val translation: Key) {
	APPLIED_BY("appliedByID", Translations.Fields.appliedBy),
	DURATION("durationMS", Translations.Fields.duration),
	REASON("reason", Translations.Fields.reason),
	TYPE("type", Translations.Fields.type),
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
	@Serializable(with = InstantInEpochMillisecondsSerializer::class)
	val liftedAt: kotlin.time.Instant? = null,
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

	val isActive get() = durationMS > 0 && liftedAt == null && activeUntil > Clock.System.now()

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
	sanctionedAt = getTimestamp("sanctionedAt").toInstant().toKotlinInstant(),
	liftedAt = getTimestamp("liftedAt")?.toInstant()?.toKotlinInstant()
)

fun getSanction(id: Int) = sqlQuery("SELECT * FROM sanctions WHERE id = ?", id) { result ->
	result.takeIf { it.next() }?.toSanction()
}

fun getSanctions(user: Snowflake, type: SanctionType? = null) = when (type) {
	null -> sqlQuery(
		"SELECT * FROM sanctions WHERE memberID = ? ORDER BY id",
		user.toString()
	) { it.mapRows(ResultSet::toSanction) }

	else -> sqlQuery(
		"SELECT * FROM sanctions WHERE memberID = ? AND type = ? ORDER BY id",
		user.toString(),
		type.storedName
	) { it.mapRows(ResultSet::toSanction) }
}

/** How many sanctions each moderator applied, the busiest first. Rows predating nullable moderators hold `'null'`. */
fun getSanctionCounts() = sqlQuery(
	"""
	SELECT appliedByID, COUNT(*) count FROM sanctions
	WHERE appliedByID IS NOT NULL AND appliedByID != 'null'
	GROUP BY appliedByID ORDER BY count DESC
	""".trimIndent()
) { result -> result.mapRows { Snowflake(it.getString("appliedByID")) to it.getInt("count") } }

/** [newValue] is untyped because `durationMS` is an integer column and the tables are STRICT. */
fun modifySanction(id: Int, value: ModifySanctionValues, newValue: Any?) =
	sqlUpdate("UPDATE sanctions SET ${value.column} = ? WHERE id = ?", newValue, id)

fun removeSanction(id: Int) = sqlUpdate("DELETE FROM sanctions WHERE id = ?", id)

/**
 * The sanctions matching every filter that was given, the most recent first.
 *
 * [reason] is matched as a substring, the `%` and `_` of a LIKE pattern staying usable on purpose.
 */
fun searchSanctions(
	member: Snowflake? = null,
	appliedBy: Snowflake? = null,
	type: SanctionType? = null,
	since: Instant? = null,
	reason: String? = null,
	limit: Int = 100,
): List<Sanction> {
	val conditions = mutableListOf<String>()
	val params = mutableListOf<Any?>()

	member?.let { conditions += "memberID = ?"; params += it.toString() }
	appliedBy?.let { conditions += "appliedByID = ?"; params += it.toString() }
	type?.let { conditions += "type = ?"; params += it.storedName }
	since?.let { conditions += "sanctionedAt >= ?"; params += Timestamp.from(it) }
	reason?.let { conditions += "reason LIKE ?"; params += "%$it%" }

	val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")} "
	params += limit

	return sqlQuery(
		"SELECT * FROM sanctions ${where}ORDER BY id DESC LIMIT ?",
		*params.toTypedArray()
	) { it.mapRows(ResultSet::toSanction) }
}

/** Temporary bans that have not been lifted yet, the ones a restart has to pick up again. */
fun getPendingTemporaryBans() = sqlQuery(
	"SELECT * FROM sanctions WHERE type = ? AND durationMS > 0 AND liftedAt IS NULL",
	SanctionType.BAN.storedName
) { it.mapRows(ResultSet::toSanction) }

fun markSanctionLifted(id: Int) = sqlUpdate(
	"UPDATE sanctions SET liftedAt = ? WHERE id = ? AND liftedAt IS NULL",
	Timestamp.from(java.time.Instant.now()),
	id
)

/** Stops every ban of [user] from counting as active, after the ban was lifted outside of the expiry sweep. */
fun liftActiveBans(user: Snowflake) = sqlUpdate(
	"UPDATE sanctions SET liftedAt = ? WHERE memberID = ? AND type = ? AND liftedAt IS NULL",
	Timestamp.from(java.time.Instant.now()),
	user.toString(),
	SanctionType.BAN.storedName
)

fun removeSanctions(user: Snowflake, type: SanctionType? = null) = when (type) {
	null -> sqlUpdate("DELETE FROM sanctions WHERE memberID = ?", user.toString())
	else -> sqlUpdate("DELETE FROM sanctions WHERE memberID = ? AND type = ?", user.toString(), type.storedName)
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
	type.storedName,
	Timestamp.from(java.time.Instant.now())
)
