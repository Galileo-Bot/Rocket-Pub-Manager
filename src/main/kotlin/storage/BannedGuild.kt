package storage

import dev.kord.common.entity.Snowflake
import extensions.ModifyGuildValues
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

data class BannedGuild(val name: String?, val id: String?, val reason: String, val bannedSince: Date) {
	operator fun get(value: ModifyGuildValues) = when (value) {
		ModifyGuildValues.NAME -> name.toString()
		ModifyGuildValues.ID -> id.toString()
		ModifyGuildValues.REASON -> reason
	}
}

private const val WHERE_NAME_OR_ID = "WHERE name = ? OR id = ?"

private fun ResultSet.toBannedGuild() = BannedGuild(
	name = getString("name"),
	id = getString("id"),
	reason = getString("reason"),
	bannedSince = getTimestamp("bannedSince")
)

fun addBannedGuild(id: Snowflake, reason: String) = addBannedGuild(id.toString(), reason)
fun addBannedGuild(name: String, reason: String, id: Snowflake? = null) {
	//language=MySQL
	sqlUpdate(
		"INSERT INTO banned_guilds (name, id, reason, bannedSince) VALUES (?, ?, ?, ?)",
		name,
		id?.toString(),
		reason,
		Timestamp.from(Instant.now())
	)
}

fun getAllBannedGuilds() = sqlQuery("SELECT * FROM banned_guilds") { it.mapRows(ResultSet::toBannedGuild) }

fun modifyGuildValue(name: String, value: ModifyGuildValues, newValue: String) {
	sqlUpdate("UPDATE banned_guilds SET ${value.column} = ? $WHERE_NAME_OR_ID", newValue, name, name)
}

fun searchBannedGuild(id: Snowflake) = searchBannedGuild(id.toString())
fun searchBannedGuild(name: String) = sqlQuery("SELECT * FROM banned_guilds $WHERE_NAME_OR_ID", name, name) { result ->
	result.takeIf { it.next() }?.toBannedGuild()
}

fun removeBannedGuild(id: Snowflake) = removeBannedGuild(id.toString())
fun removeBannedGuild(name: String) {
	sqlUpdate("DELETE FROM banned_guilds $WHERE_NAME_OR_ID", name, name)
}
