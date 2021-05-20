package storage

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@Serializable
data class Sanction(
	var type: SanctionType,
	var reason: String = "Pas de raisons définie.",
	val member: Snowflake,
	val appliedBy: Snowflake?,
	var durationMS: Int = 0
) {
	constructor(type: SanctionType, reason: String, member: Snowflake) : this(type, reason, member, null)
	constructor(type: SanctionType, reason: String, member: Snowflake, appliedBy: Snowflake) : this(type, reason, member, appliedBy, 0)
	
	@OptIn(ExperimentalTime::class)
	val duration: Duration
		get() = durationMS.toDouble().toDuration(DurationUnit.MILLISECONDS)
	
	@OptIn(ExperimentalTime::class)
	val formattedDuration: String
		get() {
			return when {
				duration.inMilliseconds == 0.0 -> ""
				duration.inHours > 24 -> " ${duration.inDays.roundToInt()}d"
				duration.inHours < 1 -> " ${duration.inMinutes.roundToInt()}m"
				else -> " ${duration.inHours.roundToInt()}h"
			}
		}
	
	fun toString(prefix: String): String {
		return "$prefix${type.name.toLowerCase()} <@${member.asString}> $reason$formattedDuration"
	}
}

enum class SanctionType {
	BAN,
	KICK,
	MUTE,
	WARN
}
