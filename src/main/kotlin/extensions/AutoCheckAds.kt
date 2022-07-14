package extensions

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.deleteIgnoringNotFound
import configuration
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.asChannelOf
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
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toCollection
import storage.Sanction
import storage.SanctionType
import utils.AD_CATEGORY_CHANNEL_EMOTE
import utils.AD_CHANNEL_EMOTE
import utils.ROCKET_PUB_GUILD
import utils.STAFF_ROLE
import utils.SanctionMessage
import utils.asSafeUsersMentions
import utils.autoSanctionEmbed
import utils.getChannelsFromSanctionMessage
import utils.getFromValue
import utils.getReasonForMessage
import utils.getVerifChannel
import utils.isAdChannel
import utils.isCategoryChannel
import utils.verificationEmbed
import kotlin.time.Duration.Companion.days

enum class ChannelAdType(private val translation: String, val sentence: String, val emote: String) : ChoiceEnum {
	CHANNEL("salon", "liste des salons de publicités", AD_CHANNEL_EMOTE),
	CATEGORY("catégorie", "liste des catégories de salons de publicités", AD_CATEGORY_CHANNEL_EMOTE);
	
	override val readableName get() = translation
}

val sanctionMessages = mutableListOf<SanctionMessage>()

class CheckAds : Extension() {
	override val name = "Auto-Check-Ads"
	
	class AddChannelArguments : Arguments() {
		val type by enumChoice<ChannelAdType> {
			name = "type"
			description = "Le type de salon à ajouter."
			typeName = "type"
		}
		
		val channel by channel {
			name = "salon"
			description = "Salon à ajouter à la liste des salons à vérifier."
			requiredGuild = { ROCKET_PUB_GUILD }
			validate {
				when (value) {
					is VoiceChannel, is StageChannel -> throw DiscordRelayedException("Ce salon est un salon vocal, il ne peut pas être utilisé pour la liste des salons à vérifier.")
					is ThreadChannel -> throw DiscordRelayedException("Ce salon est un thread, il ne peut pas être utilisé pour la liste des salons à vérifier.")
				}
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
						sanctionMessages.getFromValue(it).sanctionMessage = updateChannels(it.sanctionMessage, event.channel)
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
					autoSanctionMessage(event.message, event.member!!.getNextSanctionType(), it)
				} ?: verificationMessage(event.message)
			}
		}
	}
}

suspend fun autoSanctionMessage(message: Message, type: SanctionType, reason: String?, channel: TextChannel? = null) {
	val sanction = Sanction(type, reason ?: return, message.author!!.id)
	val channelToSend = channel ?: message.kord.getVerifChannel()
	
	val old = sanctionMessages.find {
		it.sanction.member == sanction.member && it.sanction.reason == sanction.reason && it.sanction.type == sanction.type
	}
	
	if (old != null && channel != null) {
		val channels = getChannelsFromSanctionMessage(old.sanctionMessage)
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
		channelToSend.createMessage {
			embed {
				autoSanctionEmbed(message, sanction)
			}
			components {
				addBinButtonDeleteSimilarAdsWithSanction()
			}
		}.also {
			sanctionMessages.add(SanctionMessage(message.getAuthorAsMember()!!, it, sanction))
		}
	}
}

suspend fun verificationMessage(message: Message, channel: TextChannel? = null) {
	val channelToSend = channel ?: message.kord.getVerifChannel()
	val old = getOldVerificationMessage(channelToSend, message)
	
	if (old != null && channel == null) {
		val channels = getChannelsFromSanctionMessage(old)
		channels.add(message.channel.asChannelOf())
		
		old.edit {
			embed { verificationEmbed(message, *channels.toTypedArray()) }
		}
	} else {
		channelToSend.createMessage {
			embed {
				verificationEmbed(message, message.channel.asChannelOf())
			}
			
			components {
				addVerificationButton()
				addBinButtonDeleteSimilarAds()
			}
		}
	}
}

suspend fun deleteAllSimilarAdsWithSanction(message: Message) {
	val channels = getChannelsFromSanctionMessage(message)
	val embed = message.embeds[0]
	val embedAuthor = embed.fields.find { it.name.endsWith("Par :") }?.value ?: return
	
	channels.forEach { channel ->
		val messagesBefore = channel.getMessagesBefore(channel.lastMessageId ?: return@forEach, 100)
		val messages = messagesBefore.filter { findMessage ->
			val reason = getReasonForMessage(findMessage) ?: return@filter false
			val containsReason = embed.description?.contains(reason) ?: return@filter false
			val author = findMessage.author?.fetchUserOrNull() ?: return@filter false
			
			containsReason && author.id.toString() in embedAuthor
		}.toCollection(mutableListOf())
		
		channel.lastMessage?.fetchMessageOrNull()?.let(messages::add)
		
		messages.forEach {
			it.deleteIgnoringNotFound()
		}
	}
}

suspend fun deleteAllSimilarAds(message: Message) {
	val channels = getChannelsFromSanctionMessage(message)
	val embed = message.embeds[0]
	val embedAuthor = embed.fields.find { it.name.endsWith("Par :") }?.value ?: return
	val originalAdContent = embed.description ?: return
	
	channels.forEach { channel ->
		val messagesBefore = channel.getMessagesBefore(channel.lastMessageId ?: return@forEach, 100)
		val messages = messagesBefore.filter {
			val author = it.author?.fetchUserOrNull() ?: return@filter false
			originalAdContent in it.content && author.id.toString() in embedAuthor
		}.toCollection(mutableListOf())
		
		channel.lastMessage?.fetchMessageOrNull()?.let(messages::add)
		
		messages.forEach {
			it.deleteIgnoringNotFound()
		}
	}
}

suspend fun getOldVerificationMessage(channel: TextChannel, message: Message?) = channel.messages.firstOrNull {
	if (it.embeds.isEmpty()) return@firstOrNull false
	
	val firstEmbed = it.embeds[0]
	firstEmbed.description == message?.content && firstEmbed.fields.find { it.name.endsWith("Par :") }?.value?.contains(message?.author!!.id.toString()) == true
}
