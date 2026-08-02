package entities

import bot
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Invite
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.components.ComponentContainer
import dev.kordex.core.components.publicButton
import dev.kordex.core.components.types.emoji
import dev.kordex.core.utils.deleteIgnoringNotFound
import fr.ayfri.rocketmanager.i18n.Translations
import kord
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import storage.saveAdEvent
import storage.saveVerification
import utils.*

const val DELETE_ALL_ADS_VERIF_BUTTON_ID = "delete-all-ads-verif"
const val IGNORE_VERIF_BUTTON_ID = "ignore-verif"
const val VALIDATE_VERIF_BUTTON_ID = "validate-verif"
private const val CHANNELS_EMOJI = "<:textuel:658085848092508220>"
private const val AUTHOR_FIELD_SUFFIX = "Auteur :"
private const val MESSAGES_FIELD_SUFFIX = "Messages :"
private const val MESSAGE_LINK_PREFIX = "https://discord.com/channels/"
private val ID_IN_PARENTHESES_REGEX = Regex("\\((\\d{17,20})\\)")
private val CHANNEL_MENTION_REGEX = Regex("<#\\d{17,20}>")
private const val PENDING_MESSAGES_TO_RESTORE = 100

data class VerificationMessage(
	val id: Snowflake,
	val channelId: Snowflake,
	var deleted: Boolean = false,
	val content: String = "",
) {
	val jumpUrl get() = "https://discord.com/channels/${ROCKET_PUB_GUILD.value}/${channelId}/${id}"

	suspend fun delete() {
		val kord = bot.kord
		val channel = kord.getChannelOf<TextChannel>(channelId) ?: return
		channel.getMessageOrNull(id)?.deleteIgnoringNotFound()
	}

	override fun toString(): String {
		val jumpToMessage = if (!deleted) jumpUrl else ""
		val deleted = if (deleted) "${channelId.asMention<ChannelBehavior>()} (supprimé)" else ""
		return "$jumpToMessage $deleted"
	}
}

data class Verification(
	val author: Snowflake,
	val adContent: String,
	val adMessages: MutableSet<VerificationMessage> = mutableSetOf(),
	var validatedBy: Snowflake? = null,
) {
	lateinit var verificationMessage: Message
	var lastActivityAt: Instant = Clock.System.now()
	val hasVerificationMessage get() = ::verificationMessage.isInitialized
	val isValidated get() = validatedBy != null
	val messagesFormatted get() = adMessages.joinToString("\n") { it.toString() }

	/** Only messages carrying their own content (i.e. not restored from a pre-restart embed) are compared. */
	val contentDiffers get() = adMessages.any { it.content.isNotBlank() && it.content != adContent }

	suspend fun addAdMessage(message: Message) {
		if (adMessages.any { it.channelId == message.channelId }) return

		adMessages += VerificationMessage(message.id, message.channelId, content = message.content)
		lastActivityAt = Clock.System.now()
		updateMessagesFieldInEmbed()
	}

	/** Deletes the ad messages one channel at a time, updating the embed as it goes so staff can see progress. */
	suspend fun deleteAllAds() {
		adMessages.filterNot { it.deleted }.forEach {
			it.delete()
			it.deleted = true
			updateMessagesFieldInEmbed()
		}

		if (adMessages.all { it.deleted }) {
			verificationMessage.edit { components = mutableListOf() }
			verifications.remove(this)
		}
	}

	suspend fun ignore() {
		verificationMessage.delete()
		verifications.remove(this)
	}

	suspend fun setDeletedMessage(channelId: Snowflake) {
		adMessages.find { it.channelId == channelId }?.deleted = true
		updateMessagesFieldInEmbed()
	}

	suspend fun validateBy(user: Snowflake) {
		validatedBy = user

		val verificationMessageId = verificationMessage.id
		verificationMessage.kord.getVerifLogsChannel().let {
			it.createMessage {
				embed {
					fromEmbed(verificationMessage.channel.getMessageOrNull(verificationMessageId)?.embeds!![0])

					title = "✅ Publicité validée"

					field {
						name = "<:moderator:933507900092072046> Validée par :"
						value = "${user.asMention<UserBehavior>()} (${user})"
					}
				}
			}

			saveVerification(user, verificationMessageId)
		}
		verificationMessage.delete()
		verifications.remove(this)
	}

	suspend fun updateMessagesFieldInEmbed() {
		verificationMessage.edit {
			embed {
				fromEmbed(verificationMessage.embeds[0])

				title = Translations.Embeds.Verifications.NewAd.title.translateNamed("count" to adMessages.size.toString())
				fields.find { it.name.endsWith("Messages :") }?.value = messagesFormatted

				if (contentDiffers && fields.none { it.name == Translations.Fields.warning.translate() }) {
					field {
						name = Translations.Fields.warning.translate()
						value = Translations.Embeds.Verifications.contentDiffers.translate()
					}
				}
			}
		}
	}

	suspend fun MessageCreateBuilder.generateEmbed(adMessage: Message) {
		val authorUser = adMessage.getAuthorAsMember()

		var invite: Invite? = null
		val link = findInviteCode(adContent)
		link?.let {
			runCatching {
				invite = getInvite(bot.kord, it.substringAfterLast("/"))
			}
		}

		completeEmbed(
			bot.kord,
			Translations.Embeds.Verifications.NewAd.title.translateNamed("count" to adMessages.size.toString()),
			adContent
		) {
			author {
				name = "${authorUser.username} | ${authorUser.effectiveName}"
				icon = (authorUser.avatar ?: authorUser.defaultAvatar).cdnUrl.toUrl { size = Image.Size.Size512 }
			}

			field {
				name = "<:user:933508955722899477> Auteur :"
				value = "${authorUser.mention} (${authorUser.id})"
			}

			if (link != null) {
				field {
					if (invite != null) {
						name = "📩 Invitation :"
						value = """
							Serveur : ${invite.partialGuild?.name ?: "Non trouvé."}
							ID du serveur : ${invite.partialGuild?.id?.toString() ?: "Non trouvé."}
							Nombre de membres : ${invite.approximateMemberCount ?: "Non trouvé."}
						""".trimIndent()
					} else {
						name = "Invitation :"
						value = findInviteLink(adContent)!!
					}
				}
			}

			field {
				name = "$CHANNELS_EMOJI Messages :"
				value = messagesFormatted
			}
		}
	}

	companion object {
		val verifications = ArrayDeque<Verification>(100)

		private var buttonsContainer: ComponentContainer? = null

		/**
		 * The buttons carried by every verification message.
		 *
		 * The IDs are fixed instead of the random UUIDs KordEx generates by default, and the container is
		 * shared by every message, so a single registration makes the bot answer the buttons of the messages
		 * it sent before its last restart too.
		 */
		suspend fun buttons(): ComponentContainer =
			buttonsContainer ?: buttonsWith(VALIDATE_VERIF_BUTTON_ID, DELETE_ALL_ADS_VERIF_BUTTON_ID, IGNORE_VERIF_BUTTON_ID)
				.also { buttonsContainer = it }

		private suspend fun buttonsWith(validateId: String, deleteId: String, ignoreId: String) = ComponentContainer {
			publicButton {
				id = validateId
				emoji(kord.getRocketPubGuild().getEmoji(VALID_EMOJI))
				style = ButtonStyle.Success
				label = Translations.Buttons.validateVerification

				action {
					findOrRestore(event.interaction.message)?.validateBy(event.interaction.user.id)
				}
			}

			publicButton {
				id = deleteId
				emoji("🗑")
				style = ButtonStyle.Danger
				label = Translations.Buttons.delete

				action {
					findOrRestore(message)?.deleteAllAds()
				}
			}

			publicButton {
				id = ignoreId
				emoji("🚫")
				style = ButtonStyle.Secondary
				label = Translations.Buttons.ignore

				action {
					findOrRestore(message)?.ignore()
				}
			}
		}

		/**
		 * Registers the random IDs the buttons were given before they were made fixed, so the verification
		 * messages still pending from an older run of the bot keep working instead of failing silently.
		 *
		 * TODO: Remove after September 2026, no pending verification message will predate the fixed IDs by
		 *  then, making this startup REST scan pointless.
		 */
		suspend fun registerPendingMessagesButtons(kord: Kord) {
			kord.getVerifChannel().messages
				.take(PENDING_MESSAGES_TO_RESTORE)
				.filter { it.author?.id == kord.selfId }
				.collect { message ->
					val validateId = message.buttons.find { it.style == ButtonStyle.Success }?.customId
					val deleteId = message.buttons.find { it.style == ButtonStyle.Danger }?.customId
					val ignoreId = message.buttons.find { it.style == ButtonStyle.Secondary }?.customId

					if (
						validateId == VALIDATE_VERIF_BUTTON_ID &&
						deleteId == DELETE_ALL_ADS_VERIF_BUTTON_ID &&
						ignoreId == IGNORE_VERIF_BUTTON_ID
					) return@collect
					if (validateId == null && deleteId == null && ignoreId == null) return@collect

					// The container is only built for its registration side effect, the message already exists.
					buttonsWith(
						validateId ?: VALIDATE_VERIF_BUTTON_ID,
						deleteId ?: DELETE_ALL_ADS_VERIF_BUTTON_ID,
						ignoreId ?: IGNORE_VERIF_BUTTON_ID
					)
				}
		}

		suspend fun create(adMessage: Message) = Verification(
			author = adMessage.author!!.id,
			adContent = adMessage.content,
		).apply {
			saveAdEvent(adMessage.author!!.id, adMessage.id, adMessage.channel.id)

			val verificationChannel = bot.kord.getChannelOf<TextChannel>(VERIF_CHANNEL)!!
			adMessages += VerificationMessage(adMessage.id, adMessage.channel.id, content = adMessage.content)

			val buttons = buttons()
			val verificationMessage = verificationChannel.createMessage {
				generateEmbed(adMessage)

				with(buttons) { applyToMessage() }
			}

			this.verificationMessage = verificationMessage
			verifications += this
		}

		/**
		 * The verification [message] belongs to, rebuilt from its embed when the bot restarted since it was
		 * sent and lost the in-memory one.
		 */
		suspend fun findOrRestore(message: Message) =
			verifications.find { it.hasVerificationMessage && it.verificationMessage.id == message.id }
				?: fromMessage(message)?.also { verifications += it }

		/** Reads back the state [generateEmbed] wrote, so a verification survives a restart. */
		private fun fromMessage(message: Message): Verification? {
			val embed = message.embeds.firstOrNull() ?: return null

			val author = embed.fields.find { it.name.endsWith(AUTHOR_FIELD_SUFFIX) }
				?.let { ID_IN_PARENTHESES_REGEX.find(it.value)?.groupValues?.get(1) }
				?.let { Snowflake(it) } ?: return null

			val adMessages = embed.fields.find { it.name.endsWith(MESSAGES_FIELD_SUFFIX) }
				?.value
				?.lines()
				?.mapNotNull(::parseAdMessage)
				.orEmpty()

			return Verification(author, embed.description ?: "", adMessages.toMutableSet()).apply {
				verificationMessage = message
			}
		}

		/** Parses a single line of the `Messages :` field, as formatted by [VerificationMessage.toString]. */
		private fun parseAdMessage(line: String): VerificationMessage? {
			val content = line.trim()

			if (content.startsWith(MESSAGE_LINK_PREFIX)) {
				val (channelId, messageId) = Snowflake.fromMessageLink(content.substringBefore(' '))
				return VerificationMessage(messageId, channelId)
			}

			// Deleted messages only keep their channel mention, their ID is gone with the message itself.
			val channel = CHANNEL_MENTION_REGEX.find(content)?.value ?: return null
			return VerificationMessage(Snowflake.min, Snowflake.fromChannelMention(channel), deleted = true)
		}
	}
}

fun List<Verification>.findNotValidated(adMessage: Message) = find {
	it.author == adMessage.author!!.id && !it.isValidated && it.adMessages.any { m -> m.id == adMessage.id }
}
