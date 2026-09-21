package utils

import debug
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.entity.Invite
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kordex.core.utils.deleteIgnoringNotFound
import fr.ayfri.rocketmanager.i18n.Translations
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import logger
import storage.Sanction
import storage.searchBannedGuild

data class SanctionMessage(val member: Member, var sanctionMessage: Message, val sanction: Sanction)

/** The lines of the sanctioned messages field, as written by [autoSanctionEmbed]: a jump link, followed by the deleted suffix once the ad is gone. */
fun Message.sanctionedAdLinks(): List<String> = embeds.firstOrNull()
	?.fields?.find { it.name == Translations.Embeds.autoSanctionMessages.translate() }
	?.value?.lines()?.filter(String::isNotBlank)
	.orEmpty()

/** Deletes the ads a sanction embed still lists, by ID and in parallel, the sanctioned messages are never fetched. */
suspend fun Message.deleteSanctionedAds() = coroutineScope {
	sanctionedAdLinks().filterNot { Translations.Messages.deletedSuffix.translate() in it }.forEach { link ->
		val (channelId, messageId) = Snowflake.fromMessageLink(link.substringBefore(' '))
		launch { MessageBehavior(channelId, messageId, kord).deleteIgnoringNotFound() }
	}
}

private val EVERYONE_MENTION_REGEX = Regex("@(everyone|here)")
private val WHITESPACE_REGEX = Regex("\\s")

/** What an ad check found: [reason] to sanction it, and the [invite] it links to, resolved once for the verification embed. */
class AdCheck(val reason: String?, val invite: Invite?)

/** The cheap text checks run first, the invite is only resolved (one REST request) when they all pass. */
suspend fun checkAd(message: Message): AdCheck {
	val content = message.content
	val mention = EVERYONE_MENTION_REGEX.find(content)

	val textReason = when {
		!WHITESPACE_REGEX.containsMatchIn(content) -> "Publicité sans description."
		mention != null -> "Tentative de mention `${mention.value.removePrefix("@")}`."
		else -> null
	}
	if (textReason != null) return AdCheck(textReason, null).logged(message)

	val invite = findInviteCode(content)?.let { getInvite(message.kord, it) }
	val guild = invite?.partialGuild
	val isBannedGuild = guild != null && (searchBannedGuild(guild.id) ?: searchBannedGuild(guild.name)) != null

	return AdCheck(if (isBannedGuild) "Publicité pour un serveur interdit." else null, invite).logged(message)
}

private fun AdCheck.logged(message: Message) = also {
	if (debug && reason != null) logger.debug { "Found reason in channel ${message.channelId} for message ${message.id} by ${message.author?.id} : $reason" }
}
