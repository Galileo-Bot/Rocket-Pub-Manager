package storage

import connection
import dev.kord.common.entity.Snowflake
import utils.enquote
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

fun saveVerification(verifiedBy: Snowflake, messageID: Snowflake? = null) {
	val state = connection.createStatement()
	
	val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(Instant.now())).enquote
	state.executeUpdate(
		"""
		INSERT INTO verifications (staffID, verifiedAt, messageID)
		VALUES (
			${verifiedBy.asString.enquote},
			$dateTime,
			${messageID?.asString.enquote}
		)
		"""
	)
}

fun searchVerificationMessage(messageID: Snowflake): String? {
	val state = connection.createStatement()
	val result = state.executeQuery(
		"""
			SELECT messageID FROM verifications
			WHERE messageID = ${messageID.asString.enquote}
		""".trimIndent()
	)
	result.next()
	
	return runCatching { result.getNString("messageID") }.getOrNull()
}

fun getVerificationCount(): List<Snowflake> {
	val verifications = mutableListOf<Snowflake?>()
	val state = connection.createStatement()
	val result = state.executeQuery(
		"""
		SELECT staffID FROM verifications
		""".trimIndent()
	)
	
	while (result.next()) {
		runCatching {
			val appliedBy = result.getNString("staffID")
			verifications += appliedBy?.let { Snowflake(it) }
		}
	}
	
	return verifications.filterNotNull()
}
