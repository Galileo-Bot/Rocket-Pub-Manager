package extensions

import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.addReaction
import com.kotlindiscord.kord.extensions.utils.delete
import configuration
import dev.kord.common.annotation.KordPreview
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.live.LiveMessage
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.firstOrNull
import storage.Sanction
import storage.SanctionType
import utils.STAFF_ROLE
import utils.SanctionMessage
import utils.VALID_EMOJI
import utils.asSafeUsersMentions
import utils.getChannelsFromSanctionMessage
import utils.getFromValue
import utils.getLogChannel
import utils.getReasonForMessage
import utils.id
import utils.sanctionEmbed
import utils.verificationEmbed
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class, KordPreview::class)
class CheckAds : Extension() {
	override val name = "CheckAds"
	val sanctionMessages = mutableListOf<SanctionMessage>()
	
	suspend fun EventContext<MessageCreateEvent>.createSanction(type: SanctionType, reason: String?) {
		if (reason == null) return
		val sanction = Sanction(type, reason, event.member!!.id)
		
		val old = sanctionMessages.find {
			it.sanction.member == sanction.member
				&& it.sanction.reason == sanction.reason
				&& it.sanction.type == sanction.type
		}
		
		if (old != null) {
			val channels = getChannelsFromSanctionMessage(old.sanctionMessage, bot)
			channels.add(event.message.channel.asChannel() as TextChannel)
			
			when (channels.size) {
				1 -> return
				in 5..9 -> {
					sanction.type = SanctionType.MUTE
					sanction.durationMS = channels.size.div(2).days.inWholeMilliseconds
				}
				in 10..Int.MAX_VALUE -> {
					sanction.type = SanctionType.MUTE
					sanction.durationMS = channels.size.days.inWholeMilliseconds
					sanction.reason = "Publicité dans toutes les catégories."
				}
			}
			
			sanctionMessages.getFromValue(old).sanctionMessage = old.sanctionMessage.edit {
				embed { sanctionEmbed(event, sanction, channels.toList()) }
			}
		} else {
			val message = getLogChannel().createMessage {
				embed { sanctionEmbed(event, sanction) }
			}
			
			val liveMessage = message.live()
			setBinReactionDeleteAllSimilarAds(liveMessage, message)
			
			sanctionMessages.add(SanctionMessage(event.member!!, message, sanction))
		}
		
		event.message.delete(5.minutes.inWholeMilliseconds)
	}
	
	suspend fun EventContext<MessageCreateEvent>.verificationMessage() {
		val old = getOldVerificationMessage(getLogChannel(), event.message)
		if (old != null) {
			val channels = getChannelsFromSanctionMessage(old, bot)
			channels.add(event.message.channel.asChannel() as TextChannel)
			
			old.edit {
				embed { verificationEmbed(event, channels) }
			}
		} else {
			val message = getLogChannel().createMessage {
				embed { verificationEmbed(event, mutableSetOf(event.message.channel.asChannel() as TextChannel)) }
			}
			addValidReaction(message)
			
			val liveMessage = message.live()
			setBinReactionDeleteAllSimilarAds(liveMessage, message)
			liveMessage.onReactionAdd {
				if (it.getUser().isBot || it.emoji.id != VALID_EMOJI.toString()) return@onReactionAdd
				validate(message, it)
			}
		}
	}
	
	suspend fun setBinReactionDeleteAllSimilarAds(liveMessage: LiveMessage, message: Message) {
		message.addReaction("\uD83D\uDDD1️")
		liveMessage.onReactionAdd { reactionEvent ->
			if (reactionEvent.getUser().isBot || reactionEvent.emoji.name != "\uD83D\uDDD1️") return@onReactionAdd
			
			val channels = getChannelsFromSanctionMessage(message, bot)
			channels.forEach { channel ->
				channel.messages.firstOrNull { findMessage ->
					val reason = getReasonForMessage(findMessage)
					(if (reason != null) message.embeds[0].description!!.contains(reason) else false) &&
						message.embeds[0].fields.find { it.name == "Par :" }?.value?.contains(findMessage.author!!.id.toString()) == true
				}?.delete()
			}
		}
	}
	
	suspend fun getOldVerificationMessage(channel: TextChannel, message: Message?) = channel.messages.firstOrNull {
		if (it.embeds.isEmpty()) return@firstOrNull false
		
		val firstEmbed = it.embeds[0]
		firstEmbed.description == message?.content && firstEmbed.fields.find { it.name == "Par :" }?.value?.contains(message?.author!!.id.toString()) == true
	}
	
	override suspend fun setup() {
		event<MessageDeleteEvent> {
			check { adsCheck() }
			
			action {
				if (event.message == null) return@action
				
				getReasonForMessage(event.message!!)?.let { reason ->
					sanctionMessages.find {
						it.sanction.member == event.message!!.author!!.id && it.sanction.reason == reason
					}?.let {
						sanctionMessages.getFromValue(it).sanctionMessage = updateChannels(it.sanctionMessage) ?: return@action
					}
				}
				
				getOldVerificationMessage(getLoggerChannel(), event.message)?.let { updateChannels(it) }
			}
		}
		
		event<MessageCreateEvent> {
			check {
				adsCheck()
				hasRole(STAFF_ROLE)
			}
			
			action {
				sanctionMessages.find {
					it.sanction.toString(configuration["AYFRI_ROCKETMANAGER_PREFIX"]).asSafeUsersMentions == event.message.content.asSafeUsersMentions
				}?.let {
					sanctionMessages.remove(it)
					setSanctionedBy(it.sanctionMessage, it.sanction)
				}
				
				getReasonForMessage(event.message)?.let {
					createSanction(SanctionType.WARN, it)
				} ?: verificationMessage()
			}
		}
	}
}
