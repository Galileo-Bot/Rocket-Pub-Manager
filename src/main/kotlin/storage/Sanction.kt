package storage

import connection
import dev.kord.common.entity.Snowflake
import extensions.ModifySanctionValues
import kotlinx.serialization.Serializable
import utils.enquote
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt
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
	var reason: String = "Pas de raison définie.",
	val member: Snowflake,
	val appliedBy: Snowflake?,
	var durationMS: Long = 0
) {
	constructor(type: SanctionType, reason: String, member: Snowflake) : this(type, reason, member, null)
	
	constructor(type: SanctionType, reason: String, member: Snowflake, appliedBy: Snowflake) : this(type, reason, member, appliedBy, 0)
	
	@OptIn(ExperimentalTime::class)
	val duration
		get() = durationMS.toDuration(DurationUnit.MILLISECONDS)
	
	@OptIn(ExperimentalTime::class)
	val formattedDuration: String
		get() {
			return when {
				duration.toDouble(DurationUnit.MILLISECONDS) == 0.0 -> ""
				duration.toDouble(DurationUnit.HOURS) > 24 -> " ${duration.toDouble(DurationUnit.DAYS).roundToInt()}d"
				duration.toDouble(DurationUnit.HOURS) < 1 -> " ${duration.toDouble(DurationUnit.MINUTES).roundToInt()}m"
				else -> " ${duration.toDouble(DurationUnit.HOURS).roundToInt()}h"
			}
		}
	
	fun save() = saveSanction(type, reason, member, appliedBy, durationMS)
	
	fun toString(prefix: String) = "$prefix${type.name.lowercase()} <@${member.asString}> $reason$formattedDuration"
}

fun saveSanction(type: SanctionType, reason: String, member: Snowflake, appliedBy: Snowflake? = null, durationMS: Long? = null) {
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
			${type.name.lowercase().enquote},
			$dateTime
		)
		""".trimIndent()
	)
}

fun modifySanction(id: Int, value: ModifySanctionValues, newValue: String) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		UPDATE sanctions SET ${value.name.lowercase()}=${newValue.enquote}
		WHERE ID = $id
		""".trimIndent()
	)
}

fun getSanctionCount(): List<Snowflake> {
	val sanctions = mutableListOf<Snowflake?>()
	val state = connection.createStatement()
	val result = state.executeQuery(
		"""
		SELECT appliedByID FROM sanctions ORDER BY ID
		""".trimIndent()
	)
	
	while (result.next()) {
		runCatching {
			val appliedBy = result.getNString("appliedByID")
			sanctions += appliedBy?.let { Snowflake(it) }
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
