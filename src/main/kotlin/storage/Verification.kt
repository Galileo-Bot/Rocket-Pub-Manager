package storage

import dev.kord.common.entity.Snowflake
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

fun saveVerification(verifiedBy: Snowflake, messageID: Snowflake? = null) {
	sqlUpdate(
		"INSERT INTO verifications (staffID, verifiedAt, messageID) VALUES (?, ?, ?)",
		verifiedBy.toString(),
		Timestamp.from(Instant.now()),
		messageID?.toString()
	)
}

fun searchVerificationMessage(messageID: Snowflake) = sqlQuery(
	"SELECT messageID FROM verifications WHERE messageID = ?",
	messageID.toString()
) { result -> result.takeIf { it.next() }?.getString("messageID") }

fun getVerificationCount() = sqlQuery("SELECT staffID FROM verifications") { result ->
	result.mapRows { it.getString("staffID") }
}.mapNotNull { it?.let(::Snowflake) }

fun getVerificationCountsByDay(since: Instant) = sqlQuery(
	"SELECT DATE(verifiedAt) d, COUNT(*) c FROM verifications WHERE verifiedAt >= ? GROUP BY d ORDER BY d",
	Timestamp.from(since)
) { result -> result.mapRows { DailyCount(LocalDate.parse(it.getString("d")), it.getInt("c")) } }
