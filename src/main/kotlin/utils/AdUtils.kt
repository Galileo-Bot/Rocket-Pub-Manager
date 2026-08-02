package utils

import debug
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import fr.ayfri.rocketmanager.i18n.Translations
import logger
import storage.Sanction
import storage.searchBannedGuild

data class SanctionMessage(val member: Member, var sanctionMessage: Message, val sanction: Sanction)

suspend fun getMessagesFromSanctionMessage(message: Message): MutableSet<Message> {
	val embed = message.embeds.firstOrNull() ?: return mutableSetOf()
	val field = embed.fields.find { it.name == Translations.Embeds.autoSanctionMessages.translate() }

	return field?.value?.split("\n")?.mapNotNull {
		Snowflake.fromMessageLink(it.substringBefore("_supprimé_")).let { (channelId, messageId) ->
			message.kord.getChannelOf<TextChannel>(channelId)?.getMessage(messageId)
		}
	}.orEmpty().distinctBy { it.id }.toMutableSet()
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
