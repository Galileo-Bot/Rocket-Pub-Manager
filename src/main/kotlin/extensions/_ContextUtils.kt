package extensions

import com.kotlindiscord.kord.extensions.checks.channelType
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.memberFor
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ChannelType
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.Event
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.rest.builder.message.modify.embed
import storage.Sanction
import storage.saveVerification
import storage.searchVerificationMessage
import utils.ROCKET_PUB_GUILD
import utils.SANCTION_LOGGER_CHANNEL
import utils.STAFF_ROLE
import utils.VALID_EMOJI
import utils.fromEmbedUnlessChannelField
import utils.hasRole
import utils.isInAdChannel
import utils.sanctionEmbed

suspend fun <T : Event> CheckContext<T>.adsCheck() {
	if (!passed) return
	inGuild(ROCKET_PUB_GUILD)
	channelType(ChannelType.GuildText)
	isNotBot()
	isInAdChannel()
}

suspend fun <T : Event> CheckContext<T>.isStaff() {
	if (!passed) return
	passIf { memberFor(event)?.asMemberOrNull()?.let { isStaff(it) } == true }
	fail("Vous n'avez pas le rôle Staff, cette commande ne vous est alors pas permise.")
}

suspend fun isStaff(member: MemberBehavior?) = member?.let {
	!it.asUser().isBot &&
		it.guild.id == ROCKET_PUB_GUILD &&
		it.asMember().hasRole(STAFF_ROLE)
} ?: false

fun EventContext<MessageDeleteEvent>.updateDeletedMessagesInChannelList(message: Message): List<String>? {
	val oldEmbed = message.embeds[0]
	val channels = oldEmbed.fields.find { it.name == "Salons :" }!!.value.split(Regex("\n")).toMutableList()
	val find = channels.find { it == event.channel.mention } ?: return null
	
	channels -= find
	return channels + "$find supprimé"
}

suspend fun EventContext<MessageDeleteEvent>.updateChannels(message: Message): Message? {
	val channels = updateDeletedMessagesInChannelList(message) ?: return null
	
	return message.edit {
		embed {
			fromEmbedUnlessChannelField(message.embeds[0])
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
			sanctionEmbed(event, sanction)
			field {
				name = "Sanctionnée par :"
				value = event.message.author!!.mention
			}
		}
	}
	addValidReaction(message)
}

suspend fun EventContext<MessageCreateEvent>.validate(message: Message, reactionEvent: ReactionAddEvent) {
	message.edit {
		embed {
			fromEmbedUnlessChannelField(message.embeds[0])
			title = "Publicité validée."
			field {
				name = "Validée par :"
				value = reactionEvent.user.mention
			}
		}
	}
	
	addValidReaction(message)
	if (searchVerificationMessage(message.id) == null) saveVerification(reactionEvent.userId, message.id)
}

suspend fun EventContext<MessageCreateEvent>.addValidReaction(message: Message) = message.addReaction(event.getGuild()!!.getEmoji(VALID_EMOJI))

suspend fun EventContext<MessageDeleteEvent>.getLoggerChannel() = event.guild!!.getChannel(SANCTION_LOGGER_CHANNEL) as TextChannel
suspend fun PublicSlashCommandContext<*>.respond(reply: String) = respond { content = reply }
