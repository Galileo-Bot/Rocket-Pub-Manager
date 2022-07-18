package storage

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.events.EventHandler
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.utils.canInteract
import com.kotlindiscord.kord.extensions.utils.selfMember
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import connection
import debug
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
import extensions.ModifySanctionValues
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import logger
import utils.enquote
import utils.getLogSanctionsChannel
import utils.sanctionEmbed
import utils.toUserMention
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.toDuration

enum class SanctionType(val translation: String, val emote: String) : ChoiceEnum {
	BAN("Bannissement", "<:ban:498482002601705482>"),
	KICK("Expulsion", "<:kick:933505066273501184>"),
	MUTE("Exclusion (mute)", "<:mute:933505777354834021>"),
	WARN("Avertissement", "⚠️"),
	LIGHT_WARN("Avertissement léger", "❕");
	
	override val readableName = translation
}

@Serializable
data class Sanction(
	var type: SanctionType,
	var reason: String = DEFAULT_REASON,
	val member: Snowflake,
	val id: Int = 0,
	val appliedBy: Snowflake? = null,
	var durationMS: Long = 0,
	val sanctionedAt: kotlinx.datetime.Instant = Clock.System.now(),
) {
	constructor(type: SanctionType, reason: String? = null, member: Snowflake, appliedBy: Snowflake? = null, durationMS: Long = 0) : this(
		type, reason ?: DEFAULT_REASON, member, appliedBy = appliedBy, durationMS = durationMS
	)
	
	val duration
		get() = durationMS.toDuration(DurationUnit.MILLISECONDS)
	
	fun toDiscordTimestamp(type: TimestampType) = type.format(durationMS)
	
	val isActive get() = durationMS > 0 && activeUntil > Clock.System.now()
	
	val activeUntil get() = Clock.System.now() + duration
	
	val formattedDuration: String
		get() = when {
			duration.toDouble(DurationUnit.MILLISECONDS) == 0.0 -> ""
			duration.toDouble(DurationUnit.HOURS) > 24 -> " ${duration.toDouble(DurationUnit.DAYS).roundToInt()}d"
			duration.toDouble(DurationUnit.HOURS) < 1 -> " ${duration.toDouble(DurationUnit.MINUTES).roundToInt()}m"
			else -> " ${duration.toDouble(DurationUnit.HOURS).roundToInt()}h"
		}
	
	fun equalExceptOwner(other: Sanction) =
		type == other.type &&
				reason == other.reason &&
				member == other.member &&
				abs(durationMS - other.durationMS) < 10_000
	
	suspend fun applyToMember(member: MemberBehavior, banDeleteDays: Int? = null) {
		val user = member.fetchMemberOrNull() ?: throw DiscordRelayedException("La sanction ne peut être appliquée car le membre n'a pas été trouvé.")
		if (user.guild.selfMember().fetchMemberOrNull()?.canInteract(user) != true) {
			throw DiscordRelayedException("La sanction ne peut être appliquée car le bot n'a pas les permissions suffisantes.")
		}
		
		when (type) {
			SanctionType.BAN -> member.ban {
				reason = this@Sanction.reason
				deleteMessagesDays = banDeleteDays
			}
			SanctionType.KICK -> member.kick(reason)
			SanctionType.MUTE -> member.edit {
				timeoutUntil = Clock.System.now() + duration
				reason = this@Sanction.reason
			}
			else -> return
		}
	}
	
	suspend fun sendLog(kord: Kord) {
		kord.getLogSanctionsChannel().createMessage {
			embed {
				sanctionEmbed(kord, this@Sanction)
			}
			
			allowedMentions {
				users += listOf(member)
			}
			
			content = "||${member.toUserMention()}||"
		}
		
		if (debug) logger.debug("Nouvelle sanction sauvegardée : $this")
	}
	
	suspend fun PublicSlashCommandContext<*>.sendLog() = sendLog(this@sendLog.channel.kord)
	suspend fun EventHandler<*>.sendLog() = sendLog(kord)
	
	fun save() = saveSanction(type, reason, member, appliedBy, durationMS)
	fun toString(prefix: String) = "$prefix${type.name.lowercase()} <@$member> $reason$formattedDuration"
	
	companion object {
		const val DEFAULT_REASON = "Pas de raison définie."
	}
}

val offset: ZoneOffset = ZoneOffset.ofHours(1)
val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).withZone(offset)

fun containsSanction(id: Int) = connection.createStatement().executeQuery(
	"SELECT * FROM sanctions WHERE id = $id"
).use(ResultSet::next)

fun getSanction(id: Int): Sanction? {
	val query = "SELECT * FROM sanctions WHERE id = $id"
	val result = connection.createStatement().executeQuery(query)
	
	return result.takeIf { it.next() }?.let {
		val type = SanctionType.valueOf(it.getString("type"))
		val reason = it.getString("reason")
		val member = Snowflake(it.getString("member"))
		val durationMS = it.getLong("duration")
		val appliedBy = Snowflake(it.getString("appliedBy"))
		val sanctionedAt = Instant.parse(result.getString("sanctionedAt"))
		Sanction(type, reason, member, id, appliedBy, durationMS, sanctionedAt.toKotlinInstant())
	}
}

fun getSanctions(user: Snowflake): List<Sanction> {
	val sanctions = mutableListOf<Sanction>()
	val result = connection.createStatement().executeQuery(
		"""
		SELECT * FROM sanctions
		WHERE memberID = ${user.enquote}
		""".trimIndent()
	)
	while (result.next()) {
		val type = SanctionType.valueOf(result.getString("type").uppercase())
		val reason = result.getString("reason")
		val member = Snowflake(result.getString("memberID"))
		val id = result.getInt("id")
		val appliedBy = result.getString("appliedByID").let {
			if (it != "null") Snowflake(it) else null
		}
		val durationMS = result.getLong("durationMS")
		val sanctionedAt = LocalDateTime.parse(result.getString("sanctionedAt"), formatter)
		sanctions += Sanction(type, reason, member, id, appliedBy, durationMS, sanctionedAt.toInstant(offset).toKotlinInstant())
	}
	return sanctions
}

fun getSanctionCount(): List<Snowflake> {
	val sanctions = mutableListOf<Snowflake?>()
	val result = connection.createStatement().executeQuery(
		"""
		SELECT appliedByID FROM sanctions ORDER BY ID
		""".trimIndent()
	)
	
	while (result.next()) {
		runCatching {
			val appliedBy = result.getNString("appliedByID")
			sanctions += appliedBy?.let { Snowflake(it) }
		}
	}
	
	return sanctions.filterNotNull()
}

fun modifySanction(id: Int, value: ModifySanctionValues, newValue: String) = connection.createStatement().executeUpdate(
	"""
	UPDATE sanctions SET ${value.name.lowercase()}=${newValue.enquote}
	WHERE ID = $id
	""".trimIndent()
)

fun removeSanction(id: Int) = connection.createStatement().executeUpdate(
	"""
	DELETE FROM sanctions
	WHERE ID = ${id.toString().enquote}
	""".trimIndent()
)

fun removeSanctions(user: Snowflake, type: String? = null) = connection.createStatement().executeUpdate("""
	DELETE FROM sanctions
	WHERE memberID = ${user.enquote}
	${type?.let { "AND type = ${it.lowercase().enquote}" } ?: ""}
	""".trimIndent())

fun saveSanction(type: SanctionType, reason: String, member: Snowflake, appliedBy: Snowflake? = null, durationMS: Long? = null): Int {
	val dateTime = formatter.format(Instant.now()).enquote
	return connection.createStatement().executeUpdate(
		"""
		INSERT INTO sanctions (reason, memberID, appliedByID, durationMS, type, sanctionedAt)
		VALUES (
			${reason.enquote},
			${member.enquote},
			${appliedBy.enquote},
			$durationMS,
			${type.name.lowercase().enquote},
			$dateTime
		)
		""".trimIndent()
	)
}
