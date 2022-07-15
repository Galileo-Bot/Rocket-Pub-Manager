package utils

import debug
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import logger
import storage.Sanction
import storage.searchBannedGuild

data class SanctionMessage(val member: Member, var sanctionMessage: Message, val sanction: Sanction)

suspend fun getChannelsFromSanctionMessage(message: Message): MutableSet<TextChannel> {
	val embed = message.embeds[0]
	val field = embed.fields.find { it.name.contains("Salons :") }
	
	return field!!.value.split("\n").mapNotNull {
		val id = Snowflake.forChannel(it)
		message.kord.getChannelOf<TextChannel>(id)
	}.toMutableSet()
}

suspend fun getReasonForMessage(message: Message): String? {
	val mention = Regex("@(everyone|here)").find(message.content)
	val inviteLink = findInviteCode(message.content)
	val invite = if (inviteLink != null) getInvite(message.kord, inviteLink) else null
	
	val guild = invite?.partialGuild
	val isBannedGuild = invite != null && (guild?.id?.let { searchBannedGuild(it) } != null || guild?.name?.let { searchBannedGuild(it) } != null)
	
	return when {
		!Regex("\\s").containsMatchIn(message.content) -> "Publicité sans description."
		mention != null -> "Tentative de mention `${mention.value.remove("@")}`."
		message.content == "test" -> if (debug) "Test." else null
		isBannedGuild -> "Publicité pour un serveur interdit."
		else -> null
	}?.also {
		if (debug) logger.debug("Found reason in channel ${message.channelId.enquote} for message ${message.id.enquote} by ${message.author?.id.enquote} : $it")
	}
}
