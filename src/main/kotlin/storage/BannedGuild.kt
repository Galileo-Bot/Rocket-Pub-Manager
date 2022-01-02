package storage

import connection
import dev.kord.common.entity.Snowflake
import extensions.ModifyGuildValues
import utils.enquote
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

data class BannedGuild(val name: String?, val id: String?, val reason: String, val bannedSince: Date) {
	operator fun get(value: ModifyGuildValues) = when (value) {
		ModifyGuildValues.NAME -> name.toString()
		ModifyGuildValues.ID -> id.toString()
		ModifyGuildValues.REASON -> reason
	}
}

fun addBannedGuild(id: Snowflake, reason: String) = addBannedGuild(id.asString, reason)
fun addBannedGuild(name: String, reason: String) = addBannedGuild(name, reason, null)
fun addBannedGuild(name: String, reason: String, id: Snowflake?) {
	val state = connection.createStatement()
	val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(Instant.now())).enquote
	state.executeUpdate(
		"""
		INSERT INTO banned_guilds
			(name, id, reason, bannedSince)
		VALUES (${name.enquote}, ${id?.asString.enquote}, ${reason.enquote}, $dateTime);
		""".trimIndent()
	)
}

fun searchBannedGuild(id: Snowflake) = searchBannedGuild(id.asString)
fun searchBannedGuild(name: String): BannedGuild? {
	val state = connection.createStatement()
	val result = state.executeQuery(
		"""
		SELECT * FROM banned_guilds
		${where(name)}
	""".trimIndent()
	)
	
	result.next()
	
	return try {
		BannedGuild(
			result.getNString("name"),
			result.getNString("id"),
			result.getString("reason"),
			result.getTimestamp("bannedSince")
		)
	} catch (e: Exception) {
		null
	}
}

fun modifyGuildValue(name: String, value: ModifyGuildValues, newValue: String) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		UPDATE banned_guilds SET ${value.name.lowercase()}=${newValue.enquote}
		${where(name)}
	""".trimIndent()
	)
}

fun removeBannedGuild(id: Snowflake) = removeBannedGuild(id.asString)
fun removeBannedGuild(name: String) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		DELETE FROM banned_guilds
		${where(name)}
	""".trimIndent()
	)
}

fun where(name: String) =
	"""
	WHERE NAME = ${name.enquote}
	OR ID = ${name.enquote}
""".trimIndent()
