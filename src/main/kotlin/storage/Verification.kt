package storage

import dev.kord.common.entity.Snowflake
import java.sql.Timestamp
import java.time.Instant

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
