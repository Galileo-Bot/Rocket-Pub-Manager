package extensions

import com.kotlindiscord.kord.extensions.checks.channelType
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.memberFor
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.hasPermission
import debug
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.event.Event
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.modify.embed
import storage.Sanction
import storage.saveVerification
import storage.searchVerificationMessage
import utils.ROCKET_PUB_GUILD
import utils.ROCKET_PUB_GUILD_STAFF
import utils.STAFF_ROLE
import utils.VALID_EMOJI
import utils.autoSanctionEmbed
import utils.fromEmbedUnlessChannelField
import utils.hasRole
import utils.isAdChannel
import utils.toMention

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

suspend fun <T : Event> CheckContext<T>.isStaff() {
	if (!passed) return
	val guild = guildFor(event)
	
	val inStaffGuild = guild?.id == ROCKET_PUB_GUILD_STAFF
	val isStaff = memberFor(event)?.asMemberOrNull().isStaff()
	
	failIf("Vous n'avez pas le rôle Staff, cette commande ne vous est alors pas permise.") {
		guild == null || !inStaffGuild || !isStaff
	}
}

fun updateDeletedMessagesInChannelList(sanctionMessage: Message, vararg channel: ChannelBehavior): List<String>? {
	val oldEmbed = sanctionMessage.embeds[0]
	val channels = oldEmbed.fields.find { it.name.endsWith("Salons :") }!!.value.split(Regex("\n")).toMutableList()
	val founds = channels.intersect(channel.map { it.mention }.toSet())
	channels.removeAll(founds)
	channels.addAll(founds.map { "$it supprimé" })
	return channels
}

suspend fun updateChannels(sanctionMessage: Message, vararg channel: ChannelBehavior): Message? {
	val channels = updateDeletedMessagesInChannelList(sanctionMessage, *channel) ?: return null
	
	return sanctionMessage.edit {
		embed {
			fromEmbedUnlessChannelField(sanctionMessage.embeds[0])
			field {
				name = "<:textuel:658085848092508220> Salons :"
				value = channels.joinToString("\n")
			}
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

suspend fun validate(message: Message, user: UserBehavior) {
	message.edit {
		embed {
			fromEmbedUnlessChannelField(message.embeds[0])
			title = "Publicité validée."
			field {
				name = "Validée par :"
				value = user.mention
			}
		}
	}
	message.addValidReaction()
	if (searchVerificationMessage(message.id) == null) saveVerification(user.id, message.id)
}

suspend fun Message.addValidReaction() {
	addReaction(kord.getGuild(ROCKET_PUB_GUILD, EntitySupplyStrategy.cacheWithCachingRestFallback)?.getEmoji(VALID_EMOJI) ?: return)
}

suspend fun PublicSlashCommandContext<*>.respond(reply: String) = respond { content = reply }
