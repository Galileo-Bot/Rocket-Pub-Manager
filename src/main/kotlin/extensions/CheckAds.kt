package extensions

import com.kotlindiscord.kord.extensions.checks.channelType
import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.addReaction
import com.kotlindiscord.kord.extensions.utils.delete
import configuration
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Invite
import dev.kord.core.entity.Message
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import kotlinx.coroutines.flow.firstOrNull
import storage.Sanction
import storage.SanctionType
import utils.ROCKET_PUB_GUILD
import utils.STAFF_ROLE
import utils.SanctionMessage
import utils.asSafeUsersMentions
import utils.findInviteLink
import utils.fromEmbedUnlessFields
import utils.getChannelsFromSanctionMessage
import utils.getFromValue
import utils.getLogChannel
import utils.isInAdChannel
import utils.isNotBot
import utils.removeMatches
import utils.sanctionEmbed
import utils.searchBannedGuild
import kotlin.time.ExperimentalTime
import kotlin.time.days
import kotlin.time.minutes

suspend fun adsCheck(event: MessageCreateEvent) =
	inGuild(ROCKET_PUB_GUILD)(event) &&
		channelType(ChannelType.GuildText)(event) &&
		isNotBot(event) &&
		isInAdChannel(event)


@OptIn(ExperimentalTime::class, KordPreview::class)
class CheckAds : Extension() {
	override val name = "CheckAds"
	val sanctionMessages = mutableListOf<SanctionMessage>()
	
	suspend fun getInvite(text: String): Invite? = bot.getKoin().get<Kord>().getInvite(text, false)
	
	suspend fun createSanction(event: MessageCreateEvent, type: SanctionType, reason: String?) {
		if (reason == null) return
		val sanction = Sanction(type, reason, event.member!!.id)
		
		val old = sanctionMessages.find {
			it.sanction.member == sanction.member
				&& it.sanction.reason == sanction.reason
				&& it.sanction.type == sanction.type
		}
		
		if (old != null) {
			val channels = getChannelsFromSanctionMessage(old.sanctionMessage, bot)
			channels.add(event.message.channel.asChannel())
			
			when (channels.size) {
				1 -> return
				in 5..9 -> {
					sanction.type = SanctionType.MUTE
					sanction.durationMS = channels.size.div(2).days.inMilliseconds.toInt()
				}
				
				in 10..Int.MAX_VALUE -> {
					sanction.type = SanctionType.MUTE
					sanction.durationMS = channels.size.days.inMilliseconds.toInt()
					sanction.reason = "Publicité dans toutes les catégories."
				}
			}
			
			sanctionMessages.getFromValue(old).sanctionMessage = old.sanctionMessage.edit {
				embed { sanctionEmbed(event, sanction, channels.toList())() }
			}
		} else {
			val message = getLogChannel(event).createMessage {
				embed { sanctionEmbed(event, sanction)() }
			}
			message.addReaction("\uD83D\uDDD1️")
			
			val liveMessage = message.live()
			liveMessage.onReactionAdd { reactionEvent ->
				if (reactionEvent.getUser().isBot) return@onReactionAdd
				
				val channels = getChannelsFromSanctionMessage(message, bot)
				channels.forEach {
					it.getMessagesBefore(it.getLastMessage()!!.id).firstOrNull { findMessage ->
						sanctionMessages.find { sanctionMessage ->
							sanctionMessage.sanction.reason == getReasonForMessage(findMessage) &&
								sanctionMessage.member.id == findMessage.author!!.id
						} != null
					}?.delete()
				}
			}
			
			sanctionMessages.add(SanctionMessage(event.member!!, message, sanction))
		}
		
		event.message.delete(5.minutes.toLongMilliseconds())
	}
	
	suspend fun getReasonForMessage(message: Message): String? {
		val mention = Regex("@(everyone|here)").find(message.content)
		val inviteLink = findInviteLink(message.content)
		val invite = if (inviteLink != null) getInvite(inviteLink) else null
		
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
	
	override suspend fun setup() {
		event<MessageDeleteEvent> {
			check(::adsCheck)
			
			action {
				val content = event.message?.content ?: return@action
				val mention = Regex("@(everyone|here)").find(content)
				
				val reason = when {
					!Regex("\\s").containsMatchIn(content) -> "Publicité sans description."
					mention != null -> "Tentative de mention ${mention.value}."
					content == "test" -> "Test."
					else -> null
				}
				
				if (reason != null) {
					val old = sanctionMessages.find {
						it.sanction.member == event.message!!.author!!.id
							&& it.sanction.reason == reason
					}
					
					if (old != null) {
						val oldEmbed = old.sanctionMessage.embeds[0]
						val channels = oldEmbed.fields.find { it.name == "Salons" }!!.value.split(Regex("\n")).toMutableList()
						val find = channels.find { it == event.channel.mention } ?: return@action
						
						channels.remove(find)
						channels.add(find.plus(" (supprimé)"))
						
						sanctionMessages.getFromValue(old).sanctionMessage = old.sanctionMessage.edit {
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
				}
			}
		}
		
		event<MessageCreateEvent> {
			check(/*hasRole(STAFF_ROLE), */::adsCheck)
			
			action {
				if (hasRole(STAFF_ROLE)(event)) {
					
					val sanctionMessageFind = sanctionMessages.find {
						it.sanction.toString(configuration["PREFIX"]).asSafeUsersMentions == event.message.content.asSafeUsersMentions
					}
					if (sanctionMessageFind != null) {
						sanctionMessages.remove(sanctionMessageFind)
						sanctionMessageFind.sanctionMessage.edit {
							embed {
								sanctionEmbed(event, sanctionMessageFind.sanction)()
								field {
									name = "Sanctionnée par :"
									value = event.message.author!!.mention
								}
							}
						}
						sanctionMessageFind.sanctionMessage.addReaction(event.getGuild()!!.getEmoji(Snowflake("525406069913157641")))
					}
					
				}
				createSanction(event, SanctionType.WARN, getReasonForMessage(event.message))
			}
		}
	}
}
