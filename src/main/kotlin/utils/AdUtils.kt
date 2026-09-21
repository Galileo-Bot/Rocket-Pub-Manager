package utils

import debug
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
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

suspend fun getReasonForMessage(message: Message): String? {
	val mention = Regex("@(everyone|here)").find(message.content)
	val inviteLink = findInviteCode(message.content)
	val invite = inviteLink?.let { getInvite(message.kord, it) }

	val guild = invite?.partialGuild
	val isBannedGuild = guild != null &&
		(searchBannedGuild(guild.id) ?: searchBannedGuild(guild.name)) != null

	return when {
		!Regex("\\s").containsMatchIn(message.content) -> "Publicité sans description."
		mention != null -> "Tentative de mention `${mention.value.remove("@")}`."
		message.content == "test" -> if (debug) "Test." else null
		isBannedGuild -> "Publicité pour un serveur interdit."
		else -> null
	}?.also {
		if (debug) logger.debug { "Found reason in channel ${message.channelId} for message ${message.id} by ${message.author?.id} : $it" }
	}
}
