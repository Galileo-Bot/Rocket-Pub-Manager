package extensions

import bot
import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.addReaction
import com.kotlindiscord.kord.extensions.utils.delete
import configuration
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
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
import storage.SanctionType
import utils.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

enum class ChannelAdType(val translation: String, val sentence: String, val emote: String) : ChoiceEnum {
	CHANNEL("salon", "liste des salons de publicités", AD_CHANNEL_EMOTE),
	CATEGORY("catégorie", "liste des catégories de salons de publicités", AD_CATEGORY_CHANNEL_EMOTE);
	
	override val readableName get() = translation
}

val sanctionMessages = mutableListOf<SanctionMessage>()

@OptIn(ExperimentalTime::class, KordPreview::class)
class CheckAds : Extension() {
	override val name = "CheckAds"
	
	class AddChannelArguments : Arguments() {
		val type by enumChoice<ChannelAdType>("type", "Le type de salon à ajouter.", "type")
		
		val channel by channel(displayName = "salon", description = "Salon à ajouter à la liste des salons à vérifier.", requiredGuild = { ROCKET_PUB_GUILD }) { _, channel ->
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
							if ((isTypeCategory && channel.isCategoryChannel()) || (!isTypeCategory && channel.isAdChannel())) return@collect
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
							isTypeCategory && channel.isCategoryChannel() -> respond("Ce salon est déjà dans la ${type.sentence} à vérifier.")
							!isTypeCategory && channel.isAdChannel() -> respond("Ce salon est déjà dans la ${type.sentence} à vérifier.")
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
				getReasonForMessage(event.message ?: return@action)?.let { reason ->
					sanctionMessages.find {
						it.sanction.member == event.message!!.author!!.id && it.sanction.reason == reason
					}?.let {
						sanctionMessages.getFromValue(it).sanctionMessage = updateChannels(it.sanctionMessage, event.channel) ?: return@action
					}
				}
				
				getOldVerificationMessage(kord.getVerifChannel(), event.message)?.let { updateChannels(it, event.channel) }
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
					autoSanctionMessage(event.message, SanctionType.WARN, it)
				} ?: verificationMessage(event.message)
			}
		}
	}
}

@OptIn(KordPreview::class)
suspend fun PublicSlashCommandContext<*>.autoSanctionMessage(message: Message, type: SanctionType, reason: String?) {
	val sanction = Sanction(type, reason ?: return, message.author!!.id)
	val sanctionMessage = respond {
		embed {
			autoSanctionEmbed(message, sanction)
		}
	}.message
	
	val liveMessage = sanctionMessage.live()
	setBinReactionDeleteAllSimilarAds(liveMessage, sanctionMessage)
	sanctionMessages.add(SanctionMessage(sanctionMessage.getAuthorAsMember()!!, sanctionMessage, sanction))
	
	message.delete(5.minutes.inWholeMilliseconds)
}

@OptIn(KordPreview::class)
suspend fun autoSanctionMessage(message: Message, type: SanctionType, reason: String?, channel: TextChannel? = null) {
	val sanction = Sanction(type, reason ?: return, message.author!!.id)
	val channelToSend = channel ?: message.kord.getVerifChannel()
	
	val old = sanctionMessages.find {
		it.sanction.member == sanction.member && it.sanction.reason == sanction.reason && it.sanction.type == sanction.type
	}
	
	if (old != null && channel != null) {
		val channels = getChannelsFromSanctionMessage(old.sanctionMessage, bot)
		channels.add(message.channel.asChannelOf())
		
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
			embed { autoSanctionEmbed(message, sanction, channels.toList()) }
		}
	} else {
		val sanctionMessage = channelToSend.createEmbed {
			autoSanctionEmbed(message, sanction)
		}
		
		val liveMessage = sanctionMessage.live()
		setBinReactionDeleteAllSimilarAds(liveMessage, sanctionMessage)
		
		sanctionMessages.add(SanctionMessage(sanctionMessage.getAuthorAsMember()!!, sanctionMessage, sanction))
	}
	
	message.delete(5.minutes.inWholeMilliseconds)
}

@OptIn(KordPreview::class)
suspend fun PublicSlashCommandContext<*>.verificationMessage(message: Message) {
	val verificationMessage = respond {
		embed {
			verificationEmbed(message, message.channel.asChannelOf())
		}
	}.message
	
	verificationMessage.addValidReaction()
	
	val liveMessage = verificationMessage.live()
	setBinReactionDeleteAllSimilarAds(liveMessage, verificationMessage)
	liveMessage.onReactionAdd {
		if (it.getUser().isBot || it.emoji.id != VALID_EMOJI.toString()) return@onReactionAdd
		validate(verificationMessage, it)
	}
}

@OptIn(KordPreview::class)
suspend fun verificationMessage(message: Message, channel: TextChannel? = null) {
	val channelToSend = channel ?: message.kord.getVerifChannel()
	val old = getOldVerificationMessage(channelToSend, message)
	if (old != null && channel == null) {
		val channels = getChannelsFromSanctionMessage(old, bot)
		channels.add(message.channel.asChannelOf())
		
		old.edit {
			embed { verificationEmbed(message, *channels.toTypedArray()) }
		}
	} else {
		val verificationMessage = channelToSend.createEmbed {
			verificationEmbed(message, message.channel.asChannelOf())
		}
		verificationMessage.addValidReaction()
		
		val liveMessage = verificationMessage.live()
		setBinReactionDeleteAllSimilarAds(liveMessage, verificationMessage)
		liveMessage.onReactionAdd {
			if (it.getUser().isBot || it.emoji.id != VALID_EMOJI.toString()) return@onReactionAdd
			validate(verificationMessage, it)
		}
	}
}


@OptIn(KordPreview::class)
suspend fun setBinReactionDeleteAllSimilarAds(liveMessage: LiveMessage, message: Message) {
	message.addReaction("\uD83D\uDDD1️")
	liveMessage.onReactionAdd { reactionEvent ->
		if (reactionEvent.getUser().isBot || reactionEvent.emoji.name != "\uD83D\uDDD1️") return@onReactionAdd
		
		val channels = getChannelsFromSanctionMessage(message, bot)
		channels.forEach { channel ->
			channel.messages.firstOrNull { findMessage ->
				val reason = getReasonForMessage(findMessage)
				(if (reason != null) message.embeds[0].description!!.contains(reason) else false) && message.embeds[0].fields.find { it.name == "Par :" }?.value?.contains(findMessage.author?.fetchUserOrNull()?.id.toString()) == true
			}?.delete()
		}
	}
}


suspend fun getOldVerificationMessage(channel: TextChannel, message: Message?) = channel.messages.firstOrNull {
	if (it.embeds.isEmpty()) return@firstOrNull false
	
	val firstEmbed = it.embeds[0]
	firstEmbed.description == message?.content && firstEmbed.fields.find { it.name == "Par :" }?.value?.contains(message?.author!!.id.toString()) == true
}
