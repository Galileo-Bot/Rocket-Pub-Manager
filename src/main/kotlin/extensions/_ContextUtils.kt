package extensions

import com.kotlindiscord.kord.extensions.checks.channelType
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.types.EphemeralInteractionContext
import com.kotlindiscord.kord.extensions.types.PublicInteractionContext
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.hasPermission
import debug
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.event.Event
import dev.kord.rest.builder.message.modify.embed
import storage.Sanction
import utils.*

suspend fun <T : Event> CheckContext<T>.adsCheck() {
	if (!passed) return

	inGuild(ROCKET_PUB_GUILD)
	channelType(ChannelType.GuildText)
	isNotBot()
	isAdChannel()
}

suspend fun MemberBehavior?.isStaff() = this?.let {
	if (debug && it.asMemberOrNull()?.hasPermission(Permission.Administrator) == true) return@isStaff true
	!it.asUser().isBot && it.guild.id == ROCKET_PUB_GUILD && it.asMemberOrNull()?.hasRole(STAFF_ROLE) == true
} ?: false

suspend fun MessageBehavior.removeComponents() = edit { components = mutableListOf() }

fun updateDeletedMessagesInChannelList(sanctionMessage: Message, vararg channel: ChannelBehavior): List<String> {
	val oldEmbed = sanctionMessage.embeds[0]
	val channels = oldEmbed.fields.find { it.name.endsWith("Salons :") }!!.value.split(Regex("\n")).toMutableList()
	val founds = channels.intersect(channel.map { it.mention }.toSet())
	channels.removeAll(founds)
	channels.addAll(founds.map { "$it _supprimé_" })
	return channels
}

suspend fun updateChannels(sanctionMessage: Message, vararg channel: ChannelBehavior) = sanctionMessage.edit {
	embed {
		fromEmbed(sanctionMessage.embeds[0])
		fields.removeIf { it.name.endsWith("Salons :") }

		field {
			name = "<:textuel:658085848092508220> Salons :"
			value = updateDeletedMessagesInChannelList(sanctionMessage, *channel).joinToString("\n")
		}
	}
}

suspend fun setSanctionedBy(message: Message, sanction: Sanction) {
	message.edit {
		embed {
			autoSanctionEmbed(message, sanction)
			field {
				name = "Sanctionnée par :"
				value = sanction.member.toMention<UserBehavior>()
			}
		}
	}
	message.addValidReaction()
}

suspend fun Message.addValidReaction() = addReaction(kord.getRocketPubGuild().getEmoji(VALID_EMOJI))

suspend fun PublicInteractionContext.respond(reply: String) = respond { content = reply }
suspend fun EphemeralInteractionContext.respond(reply: String) = respond { content = reply }
