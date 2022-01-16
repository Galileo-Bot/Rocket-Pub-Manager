package storage

import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.time.TimestampType
import connection
import dev.kord.common.entity.Snowflake
import extensions.ModifySanctionValues
import kotlinx.serialization.Serializable
import utils.enquote
import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

enum class SanctionType(val translation: String) : ChoiceEnum {
	BAN("Bannissement"),
	KICK("Expulsion"),
	MUTE("Exclusion"),
	WARN("Avertissement");
	
	override val readableName = translation
}

@Serializable
data class Sanction(
	var type: SanctionType,
	var reason: String = "Pas de raison définie.",
	val member: Snowflake,
	val id: Int = 0,
	val appliedBy: Snowflake? = null,
	var durationMS: Long = 0,
) {
	@OptIn(ExperimentalTime::class)
	val duration
		get() = durationMS.toDuration(DurationUnit.MILLISECONDS)
	
	fun toDiscordTimestamp(type: TimestampType) = type.format(durationMS)
	
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
	
	fun toString(prefix: String) = "$prefix${type.name.lowercase()} <@$member> $reason$formattedDuration"
}

fun containsSanction(id: Int) = connection.createStatement().executeQuery(
	"SELECT * FROM sanctions WHERE id = $id"
).use(ResultSet::next)

fun getSanction(id: Int): Sanction? {
	val query = "SELECT * FROM sanctions WHERE id = $id"
	val result = connection.createStatement().executeQuery(query)
	
	return result.takeIf { it.next() }?.let {
		val type = SanctionType.valueOf(it.getString("type"))
		val reason = it.getString("reason")
		val member = Snowflake(it.getString("member"))
		val durationMS = it.getLong("duration")
		val appliedBy = Snowflake(it.getString("appliedBy"))
		Sanction(type, reason, member, id, appliedBy, durationMS)
	}
}

fun getSanctions(user: Snowflake): List<Sanction> {
	val sanctions = mutableListOf<Sanction>()
	val result = connection.createStatement().executeQuery(
		"""
		SELECT * FROM sanctions
		WHERE memberID = ${user.toString().enquote}
		""".trimIndent()
	)
	while (result.next()) {
		val type = SanctionType.valueOf(result.getString("type").uppercase())
		val reason = result.getString("reason")
		val member = Snowflake(result.getString("memberID"))
		val id = result.getInt("id")
		val appliedBy = Snowflake(result.getString("appliedByID"))
		val durationMS = result.getLong("durationMS")
		sanctions += Sanction(type, reason, member, id, appliedBy, durationMS)
	}
	return sanctions
}

fun getSanctionCount(): List<Snowflake> {
	val sanctions = mutableListOf<Snowflake?>()
	val result = connection.createStatement().executeQuery(
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

fun modifySanction(id: Int, value: ModifySanctionValues, newValue: String) = connection.createStatement().executeUpdate(
	"""
	UPDATE sanctions SET ${value.name.lowercase()}=${newValue.enquote}
	WHERE ID = $id
	""".trimIndent()
)

fun removeSanction(id: Int) = connection.createStatement().executeUpdate(
	"""
	DELETE FROM sanctions
	WHERE ID = ${id.toString().enquote}
	""".trimIndent()
)

fun removeSanctions(user: Snowflake, type: String? = null) = connection.createStatement().executeUpdate(
	"""
	DELETE FROM sanctions
	WHERE memberID = ${user.toString().enquote}
	${type?.let { "AND type = ${it.lowercase().enquote}" } ?: ""}
	""".trimIndent()
)

fun saveSanction(type: SanctionType, reason: String, member: Snowflake, appliedBy: Snowflake? = null, durationMS: Long? = null): Int {
	val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(Instant.now())).enquote
	return connection.createStatement().executeUpdate(
		"""
		INSERT INTO sanctions (reason, memberID, appliedByID, durationMS, type, sanctionedAt)
		VALUES (
			${reason.enquote},
			${member.toString().enquote},
			${appliedBy?.toString().enquote},
			$durationMS,
			${type.name.lowercase().enquote},
			$dateTime
		)
		""".trimIndent()
	)
}
