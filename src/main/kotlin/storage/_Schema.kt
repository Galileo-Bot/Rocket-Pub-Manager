package storage

import logger
import java.sql.Connection

private const val SCHEMA_RESOURCE = "/schema.sql"

/** Creates the tables and indexes if they are missing, replacing the MariaDB image's init hook. */
fun Connection.applySchema() {
	val schema = checkNotNull(object {}.javaClass.getResource(SCHEMA_RESOURCE)) {
		"Missing $SCHEMA_RESOURCE on the classpath"
	}.readText()

	val statements = schema.lineSequence()
		.filterNot { it.trimStart().startsWith("--") }
		.joinToString("\n")
		.split(';')
		.map(String::trim)
		.filter(String::isNotEmpty)

	createStatement().use { statement ->
		statements.forEach(statement::addBatch)
		statement.executeBatch()
	}

	logger.debug { "Schema applied (${statements.size} statements)" }

	// Columns added after the first release, for the databases created before they existed.
	addMissingColumn("sanctions", "liftedAt", "TEXT NULL")
}

/** `CREATE TABLE IF NOT EXISTS` leaves existing tables alone, and SQLite has no `ADD COLUMN IF NOT EXISTS`. */
private fun Connection.addMissingColumn(table: String, column: String, definition: String) {
	val columns = createStatement().use { statement ->
		statement.executeQuery("PRAGMA table_info($table)").use { result ->
			buildList { while (result.next()) add(result.getString("name")) }
		}
	}

	if (column in columns) return

	createStatement().use { it.executeUpdate("ALTER TABLE $table ADD COLUMN $column $definition") }
	logger.info { "Added the missing $column column to $table" }
}
