package storage

import connection
import dev.kord.common.entity.Snowflake
import extensions.ModifySanctionValues
import kotlinx.serialization.Serializable
import utils.enquote
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

enum class SanctionType {
	BAN,
	KICK,
	MUTE,
	WARN
}

data class SanctionCount(val appliedBy: Snowflake?)

@Serializable
data class Sanction(
	var type: SanctionType,
	var reason: String = "Pas de raisons définie.",
	val member: Snowflake,
	val appliedBy: Snowflake?,
	var durationMS: Int = 0
) {
	constructor(type: SanctionType, reason: String, member: Snowflake) : this(type, reason, member, null)
	
	constructor(type: SanctionType, reason: String, member: Snowflake, appliedBy: Snowflake) : this(type, reason, member, appliedBy, 0)
	
	@OptIn(ExperimentalTime::class)
	val duration: Duration
		get() = durationMS.toDouble().toDuration(DurationUnit.MILLISECONDS)
	
	@OptIn(ExperimentalTime::class)
	val formattedDuration: String
		get() {
			return when {
				duration.inMilliseconds == 0.0 -> ""
				duration.inHours > 24 -> " ${duration.inDays.roundToInt()}d"
				duration.inHours < 1 -> " ${duration.inMinutes.roundToInt()}m"
				else -> " ${duration.inHours.roundToInt()}h"
			}
		}
	
	fun toString(prefix: String): String {
		return "$prefix${type.name.toLowerCase()} <@${member.asString}> $reason$formattedDuration"
	}
}

fun saveSanction(sanction: Sanction) = saveSanction(
	sanction.type,
	sanction.reason,
	sanction.member,
	sanction.appliedBy,
	sanction.durationMS
)

fun saveSanction(type: SanctionType, reason: String, member: Snowflake) = saveSanction(
	type,
	reason,
	member,
	null,
	null
)

fun saveSanction(type: SanctionType, reason: String, member: Snowflake, appliedBy: Snowflake?) = saveSanction(
	type,
	reason,
	member,
	appliedBy,
	null
)

fun saveSanction(type: SanctionType, reason: String, member: Snowflake, appliedBy: Snowflake?, durationMS: Int?) {
	val state = connection.createStatement()
	val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(Instant.now())).enquote
	state.executeUpdate(
		"""
		INSERT INTO sanctions (reason, memberID, appliedByID, durationMS, type, sanctionedAt)
		VALUES (
			${reason.enquote},
			${member.asString.enquote},
			${appliedBy?.asString.enquote},
			$durationMS,
			${type.name.toLowerCase().enquote},
			$dateTime
		)
		""".trimIndent()
	)
}

fun modifySanction(id: Int, value: ModifySanctionValues, newValue: String) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		UPDATE sanctions SET ${value.name.toLowerCase()}=${newValue.enquote}
		WHERE ID = $id
		""".trimIndent()
	)
}

fun getSanctionCount(): List<Snowflake> {
	val sanctions: MutableList<Snowflake?> = mutableListOf()
	val state = connection.createStatement()
	val result = state.executeQuery(
		"""
		SELECT appliedByID FROM sanctions ORDER BY ID
		""".trimIndent()
	)
	
	while (result.next()) {
		try {
			val appliedBy = result.getNString("appliedByID")
			sanctions += appliedBy?.let { Snowflake(it) }
		} catch (_: SQLException) {
		}
	}
	
	return sanctions.filterNotNull()
}

fun removeSanction(id: Int) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		DELETE FROM banned_guilds
		WHERE ID = $id
		""".trimIndent()
	)
}
