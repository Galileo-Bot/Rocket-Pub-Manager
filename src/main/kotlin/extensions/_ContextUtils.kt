package extensions

import debug
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.event.Event
import dev.kord.rest.builder.message.embed
import dev.kordex.core.checks.channelType
import dev.kordex.core.checks.inGuild
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.types.EphemeralInteractionContext
import dev.kordex.core.types.PublicInteractionContext
import dev.kordex.core.utils.getJumpUrl
import dev.kordex.core.utils.hasPermission
import fr.ayfri.rocketmanager.i18n.Translations
import storage.Sanction
import utils.*

suspend fun <T : Event> CheckContext<T>.adsCheck() {
	if (!passed) return

	inGuild(ROCKET_PUB_GUILD)
	channelType(ChannelType.GuildText)
	isNotBot()
	isAdChannel()
}

suspend fun MemberBehavior?.isStaff(): Boolean = this?.let {
	if (debug && it.asMemberOrNull()?.hasPermission(Permission.Administrator) == true) return@isStaff true
	!it.asUser().isBot && it.guild.id == ROCKET_PUB_GUILD && it.asMemberOrNull()?.hasRole(STAFF_ROLE) == true
} == true

suspend fun MessageBehavior.removeComponents() = edit { components = mutableListOf() }

/** Name of the embed field listing the sanctioned messages, as written by [utils.autoSanctionEmbed]. */
private val messagesFieldName get() = Translations.Embeds.autoSanctionMessages.translate()

fun updateDeletedMessagesInEmbed(sanctionMessage: Message, vararg messages: Message): List<String> {
	val oldEmbed = sanctionMessage.embeds.firstOrNull() ?: return emptyList()
	val oldMessages = oldEmbed.fields.find { it.name == messagesFieldName }
		?.value
		?.split("\n")
		?.toMutableList()
		?: return emptyList()
	val founds = oldMessages.intersect(messages.map { it.getJumpUrl() }.toSet())

	oldMessages.removeAll(founds)
	oldMessages.addAll(founds.map { "$it ${Translations.Messages.deletedSuffix.translate()}" })

	return oldMessages
}

suspend fun updateMessagesInEmbed(sanctionMessage: Message, vararg messages: Message) = sanctionMessage.edit {
	embed {
		sanctionMessage.embeds.firstOrNull()?.let { fromEmbed(it) }
		fields.removeIf { it.name == messagesFieldName }

		field {
			name = messagesFieldName
			value = updateDeletedMessagesInEmbed(sanctionMessage, *messages).joinToString("\n")
		}
	}
}

suspend fun setSanctionedBy(message: Message, sanction: Sanction) {
	message.edit {
		embed {
			autoSanctionEmbed(message, sanction)
			field {
				name = Translations.Fields.sanctionedBy.translate()
				value = sanction.member.asMention<UserBehavior>()
			}
		}
	}
	message.addValidReaction()
}

suspend fun Message.addValidReaction() = addReaction(kord.getRocketPubGuild().getEmoji(VALID_EMOJI))

suspend fun PublicInteractionContext.respond(reply: String) = respond { content = reply }
suspend fun EphemeralInteractionContext.respond(reply: String) = respond { content = reply }
