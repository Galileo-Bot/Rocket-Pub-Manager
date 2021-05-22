package utils

import com.kotlindiscord.kord.extensions.ExtensibleBot
import dev.kord.core.Kord
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import storage.Sanction

suspend fun getChannelsFromSanctionMessage(message: Message, bot: ExtensibleBot): MutableSet<TextChannel> {
	val embed = message.embeds[0]
	val field = embed.fields.find { it.name == "Salons :" }
	return field!!.value.split("\n").mapNotNull {
		val id = dev.kord.common.entity.Snowflake.forChannel(it)
		bot.getKoin().get<Kord>().getChannel(id) as TextChannel?
	}.toMutableSet()
}

data class SanctionMessage(val member: Member, var sanctionMessage: Message, val sanction: Sanction)

suspend fun getReasonForMessage(message: Message): String? {
	val mention = Regex("@(everyone|here)").find(message.content)
	val inviteLink = findInviteLink(message.content)
	val invite = if (inviteLink != null) getInvite(message.kord, inviteLink) else null
	
	val isBannedGuild = invite != null &&
		(
			invite.partialGuild?.id?.let { searchBannedGuild(it) } != null ||
				invite.partialGuild?.name?.let { searchBannedGuild(it) } != null
			)
	
	return when {
		!Regex("\\s").containsMatchIn(message.content) -> "Publicité sans description."
		mention != null -> "Tentative de mention `${mention.value.removeMatches("@")}`."
		message.content == "test" -> "Test."
		isBannedGuild -> "Publicité pour un serveur interdit."
		else -> null
	}
}
