package extensions

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.addReaction
import com.kotlindiscord.kord.extensions.utils.delete
import configuration
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.StageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.live.LiveMessage
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.firstOrNull
import storage.Sanction
import utils.AD_CATEGORY_CHANNEL_EMOTE
import utils.AD_CHANNEL_EMOTE
import utils.ROCKET_PUB_GUILD
import utils.STAFF_ROLE
import utils.SanctionMessage
import utils.VALID_EMOJI
import utils.asSafeUsersMentions
import utils.getChannelsFromSanctionMessage
import utils.getFromValue
import utils.getLogChannel
import utils.getReasonForMessage
import utils.id
import utils.isAdChannel
import utils.isCategoryChannel
import utils.sanctionEmbed
import utils.verificationEmbed
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

enum class ChannelAdType(val translation: String, val sentence: String, val emote: String) : ChoiceEnum {
	CHANNEL("salon", "liste des salons de publicités", AD_CHANNEL_EMOTE),
	CATEGORY("catégorie", "liste des catégories de salons de publicités", AD_CATEGORY_CHANNEL_EMOTE);
	
	override val readableName get() = translation
}

@OptIn(ExperimentalTime::class, KordPreview::class)
class CheckAds : Extension() {
	override val name = "CheckAds"
	val sanctionMessages = mutableListOf<SanctionMessage>()
	
	class AddChannelArguments : Arguments() {
		val type by enumChoice<ChannelAdType>("type", "Le type de salon à ajouter.", "type")
		
		val channel by channel(
			displayName = "salon",
			description = "Salon à ajouter à la liste des salons à vérifier.",
			requiredGuild = { ROCKET_PUB_GUILD }
		) { _, channel ->
			when (channel) {
				is VoiceChannel, is StageChannel -> throw DiscordRelayedException("Ce salon est un salon vocal, il ne peut pas être utilisé pour la liste des salons à vérifier.")
				is ThreadChannel -> throw DiscordRelayedException("Ce salon est un thread, il ne peut pas être utilisé pour la liste des salons à vérifier.")
			}
		}
	}
	
	override suspend fun setup() {
		publicSlashCommand(::AddChannelArguments) {
			name = "add-salon"
			description = "Ajoute un salon de publicités à la liste des salons de publicités à vérifier."
			
			guild(ROCKET_PUB_GUILD)
			
			check {
				isStaff()
				hasPermission(Permission.Administrator)
			}
			
			action {
				val type = arguments.type
				val isTypeCategory = type == ChannelAdType.CATEGORY
				when (val channel = arguments.channel.withStrategy(EntitySupplyStrategy.cacheWithCachingRestFallback).fetchChannelOrNull()) {
					is Category -> {
						val addedChannels = mutableListOf<String>()
						channel.channels.collect {
							if (it !is TextChannel) return@collect
							if ((isTypeCategory && isCategoryChannel(channel)) || (!isTypeCategory && isAdChannel(channel))) return@collect
							it.edit { topic = "${type.emote} ${it.topic}" }
							addedChannels.add(it.mention)
						}
						respond(
							"""${addedChannels.size} salons de publicités ont été ajoutés à la ${type.sentence} à vérifier :
										${addedChannels.sorted().joinToString("\n")}
										""".trimIndent()
						)
					}
					is TextChannel -> {
						when {
							isTypeCategory && isCategoryChannel(channel) -> respond("Ce salon est déjà dans la ${type.sentence} à vérifier.")
							!isTypeCategory && isAdChannel(channel) -> respond("Ce salon est déjà dans la ${type.sentence} à vérifier.")
							else -> {
								channel.edit { topic = "${type.emote} ${channel.topic}" }
								respond("Le salon ${channel.mention} a été ajouté à la ${type.sentence} à vérifier.")
							}
						}
					}
					else -> respond("Ce salon n'est pas ajoutable à la ${type.sentence} à vérifier.")
				}
			}
		}
		
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
						message.embeds[0].fields.find { it.name == "Par :" }?.value?.contains(findMessage.author?.fetchUserOrNull()?.id.toString()) == true
				}?.delete()
			}
		}
	}
	
	suspend fun getOldVerificationMessage(channel: TextChannel, message: Message?) = channel.messages.firstOrNull {
		if (it.embeds.isEmpty()) return@firstOrNull false
		
		val firstEmbed = it.embeds[0]
		firstEmbed.description == message?.content && firstEmbed.fields.find { it.name == "Par :" }?.value?.contains(message?.author!!.id.toString()) == true
	}
}
