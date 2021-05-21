package utils

import com.kotlindiscord.kord.extensions.ExtensibleBot
import dev.kord.core.Kord
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.MessageChannel
import storage.Sanction

suspend fun getChannelsFromSanctionMessage(message: Message, bot: ExtensibleBot): MutableSet<MessageChannel> {
	val embed = message.embeds[0]
	val field = embed.fields.find { it.name == "Salons" }
	return field!!.value.split(Regex("\n")).mapNotNull {
		val id = dev.kord.common.entity.Snowflake.forChannel(it)
		bot.getKoin().get<Kord>().getChannel(id) as MessageChannel?
	}.toMutableSet()
}

data class SanctionMessage(val member: Member, var sanctionMessage: Message, val sanction: Sanction)
