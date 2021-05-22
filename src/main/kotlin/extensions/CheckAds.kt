package extensions

import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
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
import kotlin.time.ExperimentalTime
import kotlin.time.days
import kotlin.time.minutes


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
			val message = getLogChannel().createMessage {
				embed { sanctionEmbed(event, sanction)() }
			}
			
			val liveMessage = message.live()
			setBinDeleteAllSimilarAds(liveMessage, message)
			
			sanctionMessages.add(SanctionMessage(event.member!!, message, sanction))
		}
		
		event.message.delete(5.minutes.toLongMilliseconds())
	}
	
	suspend fun EventContext<MessageCreateEvent>.verificationMessage() {
		val old = getOldVerificationMessage(getLogChannel(), event.message)
		if (old != null) {
			val channels = getChannelsFromSanctionMessage(old, bot)
			channels.add(event.message.channel.asChannel())
			
			old.edit {
				embed { verificationEmbed(event, channels)() }
			}
		} else {
			val message = getLogChannel().createMessage {
				embed { verificationEmbed(event, mutableSetOf(event.message.channel.asChannel()))() }
			}
			addValidReaction(message)
			
			val liveMessage = message.live()
			setBinDeleteAllSimilarAds(liveMessage, message)
			liveMessage.onReactionAdd {
				if (it.getUser().isBot || it.emoji.id != VALID_EMOJI.asString) return@onReactionAdd
				validate(message, it)
			}
		}
	}
	
	suspend fun setBinDeleteAllSimilarAds(liveMessage: LiveMessage, message: Message) {
		message.addReaction("\uD83D\uDDD1️")
		liveMessage.onReactionAdd { reactionEvent ->
			if (reactionEvent.getUser().isBot || reactionEvent.emoji.id != "\uD83D\uDDD1️") return@onReactionAdd
			
			val channels = getChannelsFromSanctionMessage(message, bot)
			channels.forEach {
				it.messages.firstOrNull { findMessage ->
					message.embeds[0].description == findMessage.content &&
						message.embeds[0].fields.find { it.name == "Par :" }?.value?.contains(findMessage.author!!.id.asString) == true
				}?.delete()
			}
		}
	}
	
	override suspend fun setup() {
		event<MessageDeleteEvent> {
			check(::adsCheck)
			
			action {
				if (event.message == null) return@action
				
				val reason = getReasonForMessage(event.message!!)
				if (reason != null) {
					val oldSanction = sanctionMessages.find {
						it.sanction.member == event.message!!.author!!.id
							&& it.sanction.reason == reason
					}
					
					if (oldSanction != null) {
						sanctionMessages.getFromValue(oldSanction).sanctionMessage = updateChannels(oldSanction.sanctionMessage) ?: return@action
					}
				}
				
				val oldMessage = getOldVerificationMessage(getLoggerChannel(), event.message)
				if (oldMessage != null) updateChannels(oldMessage)
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
						setSanctionedBy(sanctionMessageFind.sanctionMessage, sanctionMessageFind.sanction)
					}
				}
				
				val reason = getReasonForMessage(event.message)
				if (reason != null) createSanction(SanctionType.WARN, reason)
				else verificationMessage()
			}
		}
	}
	
	suspend fun getOldVerificationMessage(channel: TextChannel, message: Message?): Message? {
		return channel.messages.firstOrNull {
			if (it.embeds.isEmpty()) return@firstOrNull false
			
			it.embeds[0].description == message?.content &&
				it.embeds[0].fields.find { it.name == "Par :" }?.value?.contains(message?.author!!.id.asString) == true
		}
	}
}
