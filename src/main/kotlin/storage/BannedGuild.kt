package storage

import connection
import dev.kord.common.entity.Snowflake
import extensions.ModifyGuildValues
import utils.enquote
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

const val datePattern = "yyyy-MM-dd HH:mm:ss"

data class BannedGuild(val name: String?, val id: String?, val reason: String, val bannedSince: Date) {
	operator fun get(value: ModifyGuildValues) = when (value) {
		ModifyGuildValues.NAME -> name.toString()
		ModifyGuildValues.ID -> id.toString()
		ModifyGuildValues.REASON -> reason
	}
}

fun addBannedGuild(id: Snowflake, reason: String) = addBannedGuild(id.asString, reason)
fun addBannedGuild(name: String, reason: String, id: Snowflake? = null) {
	val state = connection.createStatement()
	val dateTime = SimpleDateFormat(datePattern).format(Date.from(Instant.now())).enquote
	state.executeUpdate(
		"""
		INSERT INTO banned_guilds
			(name, id, reason, bannedSince)
		VALUES (${name.enquote}, ${id?.asString.enquote}, ${reason.enquote}, $dateTime);
		""".trimIndent()
	)
}

fun getAllBannedGuilds(): List<BannedGuild> {
	val list = mutableListOf<BannedGuild>()
	val state = connection.createStatement()
	val result = state.executeQuery("SELECT * FROM banned_guilds")
	
	while (result.next()) {
		val name = result.getString("name")
		val id = result.getString("id")
		val reason = result.getString("reason")
		val bannedSince = result.getString("bannedSince")
		
		list += BannedGuild(name, id, reason, SimpleDateFormat(datePattern).parse(bannedSince))
	}
	
	return list
}

fun modifyGuildValue(name: String, value: ModifyGuildValues, newValue: String) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		UPDATE banned_guilds SET ${value.name.lowercase()}=${newValue.enquote}
		${whereNameOrId(name)}
	""".trimIndent()
	)
}

fun searchBannedGuild(id: Snowflake) = searchBannedGuild(id.asString)
fun searchBannedGuild(name: String): BannedGuild? {
	val state = connection.createStatement()
	val result = state.executeQuery(
		"""
		SELECT * FROM banned_guilds
		${whereNameOrId(name)}
	""".trimIndent()
	)
	
	result.next()
	
	return runCatching {
		BannedGuild(
			result.getNString("name"),
			result.getNString("id"),
			result.getString("reason"),
			result.getTimestamp("bannedSince")
		)
	}.getOrNull()
}

fun removeBannedGuild(id: Snowflake) = removeBannedGuild(id.asString)
fun removeBannedGuild(name: String) {
	val state = connection.createStatement()
	state.executeUpdate(
		"""
		DELETE FROM banned_guilds
		${whereNameOrId(name)}
	""".trimIndent()
	)
}

fun whereNameOrId(name: String) =
	"""
	WHERE NAME = ${name.enquote}
	OR ID = ${name.enquote}
""".trimIndent()
