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
