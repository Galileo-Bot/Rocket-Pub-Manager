package extensions

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
import dev.kord.rest.builder.message.allowedMentions
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.converters.impl.channel
import dev.kordex.core.components.components
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.utils.deleteIgnoringNotFound
import fr.ayfri.rocketmanager.i18n.Translations
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import storage.Sanction
import storage.SanctionType
import utils.*
import java.util.*
import kotlin.time.Duration.Companion.days

enum class ChannelAdType(private val translation: Key, val sentence: Key, val emote: String) : ChoiceEnum {
	CHANNEL(Translations.Fields.channel, Translations.Messages.adChannelsList, AD_CHANNEL_EMOTE),
	CATEGORY(
		Translations.Fields.category,
		Translations.Messages.adCategoriesList,
		AD_CATEGORY_CHANNEL_EMOTE
	);

	override val readableName get() = translation
}

val sanctionMessages = mutableListOf<SanctionMessage>()

class CheckAds : Extension() {
	override val name = "Auto-Check-Ads"

	class AddChannelArguments : Arguments() {
		val type by enumChoice<ChannelAdType> {
			name = Translations.Arguments.Type.name
			description = Translations.Arguments.Type.description
			typeName = Translations.Arguments.Type.name
		}

		val channel by channel {
			name = Translations.Arguments.Channel.name
			description = Translations.Arguments.Channel.description
			requiredGuild = { ROCKET_PUB_GUILD }
			validate {
				when (value) {
					is VoiceChannel, is StageChannel -> throw DiscordRelayedException(Translations.Errors.voiceChannelNotAllowed)
					is ThreadChannel -> throw DiscordRelayedException(Translations.Errors.threadChannelNotAllowed)
				}
			}
		}
	}

	override suspend fun setup() {

		publicSlashCommand(::AddChannelArguments) {
			name = Translations.Commands.AutoCheckAds.AddChannel.name
			description = Translations.Commands.AutoCheckAds.AddChannel.description

			guild(ROCKET_PUB_GUILD)

			action {
				val type = arguments.type
				val isTypeCategory = type == ChannelAdType.CATEGORY

				when (
					val channel = arguments.channel.withStrategy(EntitySupplyStrategy.cacheWithCachingRestFallback)
						.fetchChannelOrNull()
				) {
					is Category -> {
						val addedChannels = mutableListOf<String>()
						channel.channels.collect {
							if (it !is TextChannel) return@collect
							if ((isTypeCategory && channel.isCategoryChannel()) || (!isTypeCategory && channel.isAdChannel())) return@collect
							it.edit { topic = "${type.emote} ${it.topic}" }
							addedChannels.add(it.mention)
						}

						respond(
							Translations.Messages.channelsAddedToList.translateNamed(
								"count" to addedChannels.size.toString(),
								"list" to type.sentence,
								"channels" to addedChannels.sorted().joinToString("\n")
							)
						)
					}

					is TextChannel -> when {
						isTypeCategory && channel.isCategoryChannel() -> respond(
							Translations.Messages.channelAlreadyInList.translateNamed(
								"list" to type.sentence
							)
						)

						!isTypeCategory && channel.isAdChannel() -> respond(
							Translations.Messages.channelAlreadyInList.translateNamed(
								"list" to type.sentence
							)
						)

						else -> {
							channel.edit { topic = "${type.emote} ${channel.topic}" }
							respond(
								Translations.Messages.channelAddedToList.translateNamed(
									"channel" to channel.mention,
									"list" to type.sentence
								)
							)
						}
					}

					else -> respond(Translations.Messages.channelNotAddable.translateNamed("list" to type.sentence.translate()))
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
		val welcome =
			if (actualHour in 6..18) Translations.Messages.goodMorning.translate() else Translations.Messages.goodEvening.translate()

		val shownReason = message?.let {
			Translations.Messages.lightWarnReasonWithChannel.translateNamed(
				"reason" to reason.dropLast(1),
				"channel" to it.channel.mention
			)
		} ?: reason

		content = Translations.Messages.lightWarnMessage.translateNamed(
			"welcome" to welcome,
			"member" to member.mention,
			"reason" to shownReason
		)

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
				sanction.reason = Translations.Messages.adInAllCategories.translate()
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
		sanctionMessages.add(SanctionMessage(message.getAuthorAsMember(), it, sanction))
	}
}

suspend fun deleteAllSimilarAdsWithSanction(message: Message) =
	getMessagesFromSanctionMessage(message).forEach { it.deleteIgnoringNotFound() }
