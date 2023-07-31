package extensions

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.deleteIgnoringNotFound
import dev.kord.core.behavior.channel.TextChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.StageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import storage.Sanction
import storage.SanctionType
import utils.*
import java.util.*
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

			action {
				val type = arguments.type
				val isTypeCategory = type == ChannelAdType.CATEGORY

				when (val channel =
					arguments.channel.withStrategy(EntitySupplyStrategy.cacheWithCachingRestFallback).fetchChannelOrNull()) {
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

					is TextChannel -> when {
						isTypeCategory && channel.isCategoryChannel() -> respond("Ce salon est déjà dans la ${type.sentence} à vérifier.")
						!isTypeCategory && channel.isAdChannel() -> respond("Ce salon est déjà dans la ${type.sentence} à vérifier.")

						else -> {
							channel.edit { topic = "${type.emote} ${channel.topic}" }
							respond("Le salon ${channel.mention} a été ajouté à la ${type.sentence} à vérifier.")
						}
					}

					else -> respond("Ce salon n'est pas ajoutable à la ${type.sentence} à vérifier.")
				}
			}
		}
	}
}

suspend fun TextChannelBehavior.lightSanction(
	member: Member,
	reason: String,
	message: Message? = null,
) {
	createMessage {
		val actualHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
		val welcome = if (actualHour in 6..18) "Bonjour" else "Bonsoir"

		val shownReason = message?.let {
			"${reason.dropLast(1)}, dans le salon ${it.channel.mention} _(message supprimé)_."
		} ?: reason

		content =
			"""
				<:nope:553265076195295236> $welcome **${member.mention}**, ceci est un avertissement léger pour la raison suivante :
				> $shownReason.
				_<a:girorouge:525406076057944096> Merci de relire le règlement pour éviter d'être sanctionné._
			""".trimIndent()

		allowedMentions {
			users += member.id
		}

		Sanction(SanctionType.LIGHT_WARN, reason, member.id, appliedBy = kord.selfId).save()

		runBlocking {
			delay(5000)
			message?.delete()
		}
	}
}

suspend fun autoSanctionMessage(message: Message, type: SanctionType, reason: String?) {
	val sanction = Sanction(type, reason ?: return, message.author!!.id)
	val channelToSend = message.kord.getVerifChannel()

	val old = sanctionMessages.find {
		it.sanction.member == sanction.member && it.sanction.reason == sanction.reason && it.sanction.type == sanction.type
	}

	if (old != null) {
		val messages = getMessagesFromSanctionMessage(old.sanctionMessage)
		messages += message

		when (messages.size) {
			1 -> return

			in 5..9 -> {
				sanction.type = SanctionType.MUTE
				sanction.durationMS = messages.size.div(2).days.inWholeMilliseconds
			}

			in 10..Int.MAX_VALUE -> {
				sanction.type = SanctionType.MUTE
				sanction.durationMS = messages.size.days.inWholeMilliseconds
				sanction.reason = "Publicité dans toutes les catégories."
			}
		}

		sanctionMessages.getFromValue(old).sanctionMessage = old.sanctionMessage.edit {
			embed {
				autoSanctionEmbed(message, sanction, messages.toList())
			}
		}
		return
	}

	channelToSend.createMessage {
		embed {
			autoSanctionEmbed(message, sanction)
		}

		components {
			addBinButtonDeleteSimilarAdsWithSanction()
		}
	}.also {
		sanctionMessages.add(SanctionMessage(message.getAuthorAsMemberOrThrow(), it, sanction))
	}
}

suspend fun deleteAllSimilarAdsWithSanction(message: Message) =
	getMessagesFromSanctionMessage(message).forEach { it.deleteIgnoringNotFound() }
