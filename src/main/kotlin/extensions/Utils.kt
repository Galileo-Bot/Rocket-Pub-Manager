package extensions

import com.kotlindiscord.kord.extensions.checks.channelType
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.commands.slash.SlashCommandContext
import com.kotlindiscord.kord.extensions.events.EventContext
import dev.kord.common.entity.ChannelType
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.ReactionAddEvent
import storage.Sanction
import storage.saveVerification
import utils.ROCKET_PUB_GUILD
import utils.SANCTION_LOGGER_CHANNEL
import utils.STAFF_ROLE
import utils.VALID_EMOJI
import utils.fromEmbedUnlessChannelField
import utils.hasRole
import utils.isInAdChannel
import utils.isNotBot
import utils.sanctionEmbed

suspend fun adsCheck(event: MessageCreateEvent) =
	inGuild(ROCKET_PUB_GUILD)(event) &&
		channelType(ChannelType.GuildText)(event) &&
		isNotBot(event) &&
		isInAdChannel(event)

suspend fun isStaff(context: SlashCommandContext<out Arguments>): Boolean =
	isNotBot(context) &&
		context.getGuild()!!.asGuild().id == ROCKET_PUB_GUILD &&
		context.getMember()!!.asMember().hasRole(STAFF_ROLE)

fun EventContext<MessageDeleteEvent>.updateDeletedMessagesInChannelList(message: Message): MutableList<String>? {
	val oldEmbed = message.embeds[0]
	val channels = oldEmbed.fields.find { it.name == "Salons :" }!!.value.split(Regex("\n")).toMutableList()
	val find = channels.find { it == event.channel.mention } ?: return null
	
	channels.remove(find)
	channels.add(find.plus(" (supprimé)"))
	return channels
}

suspend fun EventContext<MessageDeleteEvent>.updateChannels(message: Message): Message? {
	val oldEmbed = message.embeds[0]
	val channels = updateDeletedMessagesInChannelList(message) ?: return null
	
	return message.edit {
		embed {
			fromEmbedUnlessChannelField(oldEmbed)
			field {
				name = "Salons :"
				value = channels.joinToString("\n")
			}
		}
	}
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
	addValidReaction(message)
}

suspend fun EventContext<MessageCreateEvent>.validate(message: Message, reactionEvent: ReactionAddEvent) {
	val oldEmbed = message.embeds[0]
	
	message.edit {
		embed {
			fromEmbedUnlessChannelField(oldEmbed)
			field {
				name = "Validée par :"
				value = reactionEvent.user.mention
			}
		}
	}
	addValidReaction(message)
	saveVerification(reactionEvent.userId, message.id)
}

suspend fun EventContext<MessageCreateEvent>.addValidReaction(message: Message) {
	message.addReaction(event.getGuild()!!.getEmoji(VALID_EMOJI))
}

suspend fun EventContext<MessageDeleteEvent>.getLoggerChannel(): TextChannel {
	return event.guild!!.getChannel(SANCTION_LOGGER_CHANNEL) as TextChannel
}
