package extensions

import com.kotlindiscord.kord.extensions.events.EventContext
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import storage.Sanction
import utils.SANCTION_LOGGER_CHANNEL
import utils.fromEmbedUnlessFields
import utils.sanctionEmbed

suspend fun EventContext<MessageDeleteEvent>.updateChannels(message: Message): Message? {
	val oldEmbed = message.embeds[0]
	val channels = updateDeletedMessagesInChannelList(message) ?: return null
	
	return message.edit {
		embed {
			fromEmbedUnlessFields(oldEmbed)
			
			field {
				name = "Utilisateur incriminé :"
				value = "${event.message?.author?.tag} (`${event.message?.author?.id?.asString}`)"
			}
			
			field {
				name = "Salons"
				value = channels.joinToString("\n")
			}
		}
	}
}

fun EventContext<MessageDeleteEvent>.updateDeletedMessagesInChannelList(message: Message): MutableList<String>? {
	val oldEmbed = message.embeds[0]
	val channels = oldEmbed.fields.find { it.name == "Salons" }!!.value.split(Regex("\n")).toMutableList()
	val find = channels.find { it == event.channel.mention } ?: return null
	
	channels.remove(find)
	channels.add(find.plus(" (supprimé)"))
	return channels
}

suspend fun EventContext<MessageCreateEvent>.setSanctionedBy(message: Message, sanction: Sanction) {
	message.edit {
		embed {
			sanctionEmbed(event, sanction)()
			field {
				name = "Sanctionnée par :"
				value = event.message.author!!.mention
			}
		}
	}
	message.addReaction(event.getGuild()!!.getEmoji(Snowflake("525406069913157641")))
}

suspend fun EventContext<MessageDeleteEvent>.getLoggerChannel(): TextChannel {
	return event.guild!!.getChannel(SANCTION_LOGGER_CHANNEL) as TextChannel
}
