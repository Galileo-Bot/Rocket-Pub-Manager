package storage

import connection
import java.sql.ResultSet

/** The single in-process connection is not thread-safe, and events land on any dispatcher. */
private val databaseLock = Any()

/**
 * Runs [query] as a prepared statement bound to [params], closing the statement afterwards.
 *
 * Using bound parameters instead of string interpolation keeps user-provided values out of the SQL text.
 */
fun sqlUpdate(query: String, vararg params: Any?) = synchronized(databaseLock) {
	connection.prepareStatement(query).use { statement ->
		statement.bind(params)
		statement.executeUpdate()
	}
}

/** Runs [query] as a prepared statement and hands the result set to [block], closing both afterwards. */
fun <T> sqlQuery(query: String, vararg params: Any?, block: (ResultSet) -> T): T = synchronized(databaseLock) {
	connection.prepareStatement(query).use { statement ->
		statement.bind(params)
		statement.executeQuery().use(block)
	}
}

/** Maps every remaining row of the result set through [transform]. */
fun <T> ResultSet.mapRows(transform: (ResultSet) -> T) = buildList {
	while (next()) add(transform(this@mapRows))
}

private fun java.sql.PreparedStatement.bind(params: Array<out Any?>) =
	params.forEachIndexed { index, param -> setObject(index + 1, param) }
