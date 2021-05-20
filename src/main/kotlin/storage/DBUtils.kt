package storage

import connection
import dev.kord.common.entity.Snowflake
import extensions.ModifyGuildValues
import java.sql.Statement
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

fun addBannedGuild(id: Snowflake, reason: String) = addBannedGuild(id.asString, reason)

fun addBannedGuild(name: String, reason: String) {
	val state = connection.createStatement()
	val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(Instant.now()))
	
	state.executeUpdate(
		"""
		INSERT INTO banned_guilds
			(name, reason, bannedSince)
		VALUES (${state.enquoteLiteral(name)}, ${state.enquoteLiteral(reason)}, ${state.enquoteLiteral(dateTime)});
		""".trimIndent()
	)
}

fun addBannedGuild(id: Snowflake, reason: String, name: String) {
	val state = connection.createStatement()
	val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(Instant.now()))
	state.executeUpdate(
		"""
		INSERT INTO banned_guilds
			(name, id, reason, bannedSince)
		VALUES ($name, ${id.asString}, $reason, $dateTime);
		""".trimIndent()
	)
}

data class BannedGuild(val name: String?, val id: String?, val reason: String, val bannedSince: Date) {
	operator fun get(value: ModifyGuildValues): String {
		return when(value) {
			ModifyGuildValues.NAME -> name.toString()
			ModifyGuildValues.ID -> id.toString()
			ModifyGuildValues.REASON -> reason
		}
		
	}
}

fun searchBannedGuild(id: Snowflake) = searchBannedGuild(id.asString)

fun searchBannedGuild(name: String): BannedGuild? {
	val state = connection.createStatement()
	val result = state.executeQuery(
		"""
		SELECT * FROM banned_guilds
		${where(state, name)}
	""".trimIndent()
	)
	
	result.next()
	
	return if (
		result.getNString("name") == null &&
		result.getNString("id") == null
	) {
		null
	} else {
		BannedGuild(
			result.getNString("name"),
			result.getNString("id"),
			result.getString("reason"),
			result.getTimestamp("bannedSince")
		)
	}
}

fun removeBannedGuild(id: Snowflake) = removeBannedGuild(id.asString)

fun removeBannedGuild(name: String) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		DELETE FROM banned_guilds
		${where(state, name)}
	""".trimIndent()
	)
}

fun modifyGuildValue(name: String, value: ModifyGuildValues, newValue: String) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		UPDATE banned_guilds SET ${value.name.toLowerCase()}=${state.enquoteLiteral(newValue)}
		${where(state, name)}
	""".trimIndent()
	)
}

fun where(state: Statement, name: String) =
	"""WHERE NAME = ${state.enquoteLiteral(name)}
			OR ID = ${state.enquoteLiteral(name)}"""
