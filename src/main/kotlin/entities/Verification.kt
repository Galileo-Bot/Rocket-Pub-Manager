package entities

import bot
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.utils.deleteIgnoringNotFound
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Invite
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kord
import storage.saveVerification
import utils.*

const val DELETE_ALL_ADS_VERIF_BUTTON_ID = "delete-all-ads-verif"
const val VALIDATE_VERIF_BUTTON_ID = "validate-verif"
private const val CHANNELS_EMOJI = "<:textuel:658085848092508220>"

data class VerificationMessage(
	val id: Snowflake,
	val channelId: Snowflake,
	var deleted: Boolean = false,
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
	val isValidated get() = validatedBy != null
	val messagesFormatted get() = adMessages.joinToString("\n") { it.toString() }

	suspend fun addAdMessage(message: Message) {
		if (adMessages.any { it.channelId == message.channelId }) return

		adMessages += VerificationMessage(message.id, message.channelId)
		updateMessagesFieldInEmbed()
	}

	suspend fun deleteAllAds() = adMessages.forEach { it.delete() }

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
	}

	suspend fun updateMessagesFieldInEmbed() {
		verificationMessage.edit {
			embed {
				fromEmbed(verificationMessage.embeds[0])

				fields.find { it.name.endsWith("Messages :") }?.value = messagesFormatted
			}
		}
	}

	suspend fun MessageCreateBuilder.generateEmbed(adMessage: Message) {
		val authorUser = adMessage.getAuthorAsMemberOrThrow()

		var invite: Invite? = null
		val link = findInviteCode(adContent)
		link?.let {
			runCatching {
				invite = getInvite(bot.kord, it.substringAfterLast("/"))
			}
		}

		completeEmbed(bot.kord, "Nouvelle publicité à valider.", adContent) {
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
						name = "\uD83D\uDCE9 Invitation :"
						value = """
							Serveur : ${invite?.partialGuild?.name ?: "Non trouvé."}
							ID du serveur : ${invite?.partialGuild?.id?.toString() ?: "Non trouvé."}
							Nombre de membres : ${invite?.approximateMemberCount ?: "Non trouvé."}
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

		suspend fun create(adMessage: Message) = Verification(
			author = adMessage.author!!.id,
			adContent = adMessage.content,
		).apply {
			val verificationChannel = bot.kord.getChannelOf<TextChannel>(VERIF_CHANNEL)!!
			adMessages += VerificationMessage(adMessage.id, adMessage.channel.id)

			val verificationMessage = verificationChannel.createMessage {
				generateEmbed(adMessage)

				components {
					publicButton {
						emoji(kord.getRocketPubGuild().getEmoji(VALID_EMOJI))
						style = ButtonStyle.Success
						label = "Valider"

						action {
							verifications.find {
								it.verificationMessage.id == event.interaction.message.id
							}?.validateBy(event.interaction.user.id)
						}
					}

					publicButton {
						emoji("\uD83D\uDDD1")
						style = ButtonStyle.Danger
						label = "Supprimer"

						action {
							verifications.find {
								it.verificationMessage.id == message.id
							}?.deleteAllAds()
						}
					}
				}
			}

			this.verificationMessage = verificationMessage
			verifications += this
		}
	}
}

fun List<Verification>.findNotValidated(adMessage: Message) = find {
	it.adContent == adMessage.content && it.author == adMessage.author!!.id && !it.isValidated
}
