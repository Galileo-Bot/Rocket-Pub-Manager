package storage

import dev.kord.common.entity.Snowflake
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

data class DailyCount(val day: LocalDate, val count: Int)

fun saveAdEvent(author: Snowflake, messageID: Snowflake, channelID: Snowflake) = sqlUpdate(
	"INSERT INTO ad_events (authorID, messageID, channelID, postedAt) VALUES (?, ?, ?, ?)",
	author.toString(),
	messageID.toString(),
	channelID.toString(),
	Timestamp.from(Instant.now())
)

fun getAdCountsByDay(since: Instant) = sqlQuery(
	"SELECT DATE(postedAt) d, COUNT(*) c FROM ad_events WHERE postedAt >= ? GROUP BY d ORDER BY d",
	Timestamp.from(since)
) { result -> result.mapRows { DailyCount(LocalDate.parse(it.getString("d")), it.getInt("c")) } }

data class AdMessage(val channelID: Snowflake, val messageID: Snowflake)

/** Every ad [author] posted, the oldest first, so they can be deleted without scanning the channels. */
fun getAdMessages(author: Snowflake) = sqlQuery(
	"SELECT channelID, messageID FROM ad_events WHERE authorID = ? ORDER BY id",
	author.toString()
) { result -> result.mapRows { AdMessage(Snowflake(it.getString("channelID")), Snowflake(it.getString("messageID"))) } }

fun getAdEventCount(author: Snowflake) = sqlQuery(
	"SELECT COUNT(*) c FROM ad_events WHERE authorID = ?",
	author.toString()
) { result -> result.takeIf { it.next() }?.getInt("c") ?: 0 }
