package storage

import debug
import dev.kord.common.entity.Snowflake
import dev.kord.common.serialization.InstantInEpochMillisecondsSerializer
import dev.kord.core.Kord
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.rest.builder.message.allowedMentions
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.commands.application.slash.PublicSlashCommandContext
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.events.EventHandler
import dev.kordex.i18n.Key
import dev.kordex.core.time.TimestampType
import dev.kordex.core.types.EphemeralInteractionContext
import dev.kordex.core.types.PublicInteractionContext
import dev.kordex.core.utils.canInteract
import dev.kordex.core.utils.selfMember
import dev.kordex.core.utils.timeoutUntil
import extensions.ModifySanctionValues
import fr.ayfri.rocketmanager.i18n.Translations
import kotlin.time.Clock
import kotlin.time.toKotlinInstant
import kotlinx.serialization.Serializable
import logger
import utils.asMention
import utils.getLogSanctionsChannel
import utils.sanctionEmbed
import java.sql.ResultSet
import java.sql.Timestamp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit
import kotlin.time.toDuration

enum class SanctionType(val translation: Key, val emote: String) : ChoiceEnum {
	BAN(Translations.SanctionTypes.ban, "<:ban:498482002601705482>"),
	KICK(Translations.SanctionTypes.kick, "<:kick:933505066273501184>"),
	MUTE(Translations.SanctionTypes.mute, "<:mute:933505777354834021>"),
	WARN(Translations.SanctionTypes.warn, "⚠️"),
	LIGHT_WARN(Translations.SanctionTypes.lightWarn, "❕");

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
	@Serializable(with = InstantInEpochMillisecondsSerializer::class)
	val sanctionedAt: kotlin.time.Instant = Clock.System.now(),
) {
	constructor(
		type: SanctionType,
		reason: String? = null,
		member: Snowflake,
		appliedBy: Snowflake? = null,
		durationMS: Long = 0
	) : this(
		type, reason ?: DEFAULT_REASON, member, appliedBy = appliedBy, durationMS = durationMS
	)

	val duration
		get() = durationMS.toDuration(DurationUnit.MILLISECONDS)

	fun toDiscordTimestamp(type: TimestampType) = type.format(durationMS)

	val isActive get() = durationMS > 0 && activeUntil > Clock.System.now()

	val activeUntil get() = sanctionedAt + duration

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
		val user = member.fetchMemberOrNull()
			?: throw DiscordRelayedException(Translations.Errors.memberNotFound)
		if (user.guild.selfMember().fetchMemberOrNull()?.canInteract(user) != true) {
			throw DiscordRelayedException(Translations.Errors.insufficientPermissions)
		}

		when (type) {
			SanctionType.BAN -> member.ban {
				reason = this@Sanction.reason
				deleteMessageDuration = banDeleteDays?.days
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

			content = "||${member.asMention<UserBehavior>()}||"
		}

		if (debug) logger.debug { "Nouvelle sanction sauvegardée : $this" }
	}

	suspend fun PublicSlashCommandContext<*, *>.sendLog() = sendLog(this@sendLog.channel.kord)
	suspend fun EventHandler<*>.sendLog() = sendLog(kord)

	suspend fun PublicInteractionContext.replyWithSanctionEmbed() {
		respond {
			sanctionEmbed(interactionResponse.kord, this@Sanction)
		}
	}

	suspend fun EphemeralInteractionContext.replyWithSanctionEmbed() {
		respond {
			sanctionEmbed(interactionResponse.kord, this@Sanction)
		}
	}

	fun save() = saveSanction(type, reason, member, appliedBy, durationMS)
	fun toString(prefix: String) = "$prefix${type.name.lowercase()} <@$member> $reason$formattedDuration"

	companion object {
		const val DEFAULT_REASON = "Pas de raison définie."
	}
}

private fun ResultSet.toSanction() = Sanction(
	type = SanctionType.valueOf(getString("type").uppercase()),
	reason = getString("reason"),
	member = Snowflake(getString("memberID")),
	id = getInt("id"),
	appliedBy = getString("appliedByID")?.takeIf { it != "null" }?.let(::Snowflake),
	durationMS = getLong("durationMS"),
	sanctionedAt = getTimestamp("sanctionedAt").toInstant().toKotlinInstant()
)

fun getSanction(id: Int) = sqlQuery("SELECT * FROM sanctions WHERE id = ?", id) { result ->
	result.takeIf { it.next() }?.toSanction()
}

fun getSanctions(user: Snowflake) = sqlQuery(
	"SELECT * FROM sanctions WHERE memberID = ?",
	user.toString()
) { it.mapRows(ResultSet::toSanction) }

fun getSanctionCount() = sqlQuery("SELECT appliedByID FROM sanctions ORDER BY id") { result ->
	result.mapRows { it.getString("appliedByID") }
}.mapNotNull { it?.takeIf { id -> id != "null" }?.let(::Snowflake) }

fun modifySanction(id: Int, value: ModifySanctionValues, newValue: String) =
	sqlUpdate("UPDATE sanctions SET ${value.column} = ? WHERE id = ?", newValue, id)

fun removeSanction(id: Int) = sqlUpdate("DELETE FROM sanctions WHERE id = ?", id)

fun removeSanctions(user: Snowflake, type: String? = null) = when (type) {
	null -> sqlUpdate("DELETE FROM sanctions WHERE memberID = ?", user.toString())
	else -> sqlUpdate("DELETE FROM sanctions WHERE memberID = ? AND type = ?", user.toString(), type.lowercase())
}

fun saveSanction(
	type: SanctionType,
	reason: String,
	member: Snowflake,
	appliedBy: Snowflake? = null,
	durationMS: Long? = null
) = sqlUpdate(
	"""
	INSERT INTO sanctions (reason, memberID, appliedByID, durationMS, type, sanctionedAt)
	VALUES (?, ?, ?, ?, ?, ?)
	""".trimIndent(),
	reason,
	member.toString(),
	appliedBy?.toString(),
	durationMS ?: 0L,
	type.name.lowercase(),
	Timestamp.from(java.time.Instant.now())
)
