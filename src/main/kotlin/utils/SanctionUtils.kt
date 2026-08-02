package utils

import debug
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.rest.builder.message.allowedMentions
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.types.EphemeralInteractionContext
import dev.kordex.core.types.PublicInteractionContext
import dev.kordex.core.utils.canInteract
import dev.kordex.core.utils.selfMember
import dev.kordex.core.utils.timeoutUntil
import dev.kordex.i18n.Key
import fr.ayfri.rocketmanager.i18n.Translations
import kotlin.time.Clock
import logger
import storage.Sanction
import storage.SanctionType
import storage.getSanctions
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Carries out the Discord side of the sanction, the types with no Discord counterpart being no-ops. */
suspend fun Sanction.applyToMember(member: MemberBehavior, banDeleteDays: Int? = null) {
	val target = member.fetchMemberOrNull()
		?: throw DiscordRelayedException(Translations.Errors.memberNotFound)
	if (target.guild.selfMember().fetchMemberOrNull()?.canInteract(target) != true) {
		throw DiscordRelayedException(Translations.Errors.insufficientPermissions)
	}

	when (type) {
		SanctionType.BAN -> member.ban {
			reason = this@applyToMember.reason
			deleteMessageDuration = banDeleteDays?.days
		}

		SanctionType.KICK -> member.kick(reason)
		SanctionType.MUTE -> member.edit {
			timeoutUntil = Clock.System.now() + duration
			reason = this@applyToMember.reason
		}

		SanctionType.WARN, SanctionType.LIGHT_WARN -> Unit
	}
}

suspend fun Sanction.sendLog(kord: Kord) {
	kord.getLogSanctionsChannel().createMessage {
		embed {
			sanctionEmbed(kord, this@sendLog)
		}

		allowedMentions {
			users += listOf(member)
		}

		content = "||${member.asMention<UserBehavior>()}||"
	}

	if (debug) logger.debug { "Nouvelle sanction sauvegardée : $this" }
}

/** Fails the command when the bot cannot moderate [member], with a message naming the sanction that was refused. */
suspend fun GuildBehavior?.ensureCanInteract(member: MemberBehavior, error: Key) {
	val self = this?.fetchGuildOrNull()?.selfMember()?.fetchMemberOrNull()
	val target = member.fetchMemberOrNull()
	if (target == null || self?.canInteract(target) != true) throw DiscordRelayedException(error)
}

/** Applies the sanction on Discord when [member] is given, records it, logs it and answers with its embed. */
suspend fun PublicInteractionContext.applySanction(
	sanction: Sanction,
	member: MemberBehavior? = null,
	banDeleteDays: Int? = null,
) {
	val kord = interactionResponse.kord

	member?.let { sanction.applyToMember(it, banDeleteDays) }
	sanction.save()
	sanction.sendLog(kord)

	respond {
		sanctionEmbed(kord, sanction)
	}
}

suspend fun PublicInteractionContext.replyWithSanctionEmbed(sanction: Sanction) = respond {
	sanctionEmbed(interactionResponse.kord, sanction)
}

suspend fun EphemeralInteractionContext.replyWithSanctionEmbed(sanction: Sanction) = respond {
	sanctionEmbed(interactionResponse.kord, sanction)
}

fun UserBehavior.getNextMuteDuration() = when (getSanctions(id).size) {
	0 -> 0.seconds
	in 1..2 -> 6.hours
	in 3..4 -> 1.days
	in 5..6 -> 5.days
	in 7..8 -> 14.days
	else -> 27.days
}.inWholeMilliseconds

fun UserBehavior.getNextSanctionType(): SanctionType {
	val sanctions = getSanctions(id)
	val types = sanctions.mapTo(mutableSetOf()) { it.type }

	return when (sanctions.size) {
		0 -> SanctionType.LIGHT_WARN

		in 1..4 -> when {
			SanctionType.MUTE in types -> SanctionType.MUTE
			SanctionType.KICK in types -> SanctionType.KICK
			SanctionType.BAN in types -> SanctionType.BAN
			else -> SanctionType.WARN
		}

		in 5..10 -> when {
			SanctionType.MUTE in types -> SanctionType.KICK
			SanctionType.KICK in types -> SanctionType.BAN
			else -> SanctionType.WARN
		}

		else -> SanctionType.BAN
	}
}
