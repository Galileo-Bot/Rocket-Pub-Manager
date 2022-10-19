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
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kord
import storage.saveVerification
import utils.ROCKET_PUB_GUILD
import utils.VALID_EMOJI
import utils.VERIF_CHANNEL
import utils.completeEmbed
import utils.findInviteCode
import utils.findInviteLink
import utils.fromEmbed
import utils.getInvite
import utils.getRocketPubGuild
import utils.getVerifLogsChannel
import utils.toMention

const val DELETE_ALL_ADS_VERIF_BUTTON_ID = "delete-all-ads-verif"
const val VALIDATE_VERIF_BUTTON_ID = "validate-verif"
private const val CHANNELS_EMOJI = "<:textuel:658085848092508220>"

data class VerificationMessage(
	val id: Snowflake,
	val channelId: Snowflake,
	var deleted: Boolean = false,
) {
	suspend fun delete() {
		val kord = bot.kord
		val channel = kord.getChannelOf<TextChannel>(channelId) ?: return
		channel.getMessageOrNull(id)?.deleteIgnoringNotFound()
	}
	
	override fun toString(): String {
		val jumpToMessage = if (!deleted) "[Aller au message](https://discord.com/channels/${ROCKET_PUB_GUILD.value}/${channelId}/${id})" else ""
		val deleted = if (deleted) "(supprimé)" else ""
		return "${channelId.toMention<ChannelBehavior>()} $jumpToMessage $deleted"
	}
}

data class Verification(
	val author: Snowflake,
	val adContent: String,
	val adChannels: MutableSet<VerificationMessage> = mutableSetOf(),
	var validatedBy: Snowflake? = null,
) {
	lateinit var verificationMessage: Message
	val isValidated get() = validatedBy != null
	val channelsFormatted get() = adChannels.joinToString("\n") { it.toString() }
	
	suspend fun addAdChannel(message: Message) {
		if (adChannels.any { it.channelId == message.channelId }) return
		
		adChannels += VerificationMessage(message.id, message.channelId)
		updateChannelsFieldInEmbed()
	}
	
	suspend fun deleteAllAds() = adChannels.forEach { it.delete() }
	
	suspend fun setDeletedChannel(channelId: Snowflake) {
		adChannels.find { it.channelId == channelId }?.deleted = true
		updateChannelsFieldInEmbed()
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
						value = "${user.toMention<UserBehavior>()} (${user})"
					}
				}
			}
			
			saveVerification(user, verificationMessageId)
		}
		verificationMessage.delete()
	}
	
	suspend fun updateChannelsFieldInEmbed() {
		verificationMessage.edit {
			embed {
				fromEmbed(verificationMessage.embeds[0])
				
				fields.find { it.name.endsWith("Salons :") }?.value = channelsFormatted
			}
		}
	}
	
	suspend fun MessageCreateBuilder.generateEmbed(adMessage: Message) {
		val authorUser = adMessage.getAuthorAsMember()!!
		
		var invite: Invite? = null
		val link = findInviteCode(adContent)
		link?.let {
			runCatching {
				invite = getInvite(bot.kord, it.substringAfterLast("/"))
			}
		}
		
		completeEmbed(bot.kord, "Nouvelle publicité à valider.", adContent) {
			author {
				name = "${authorUser.tag} | ${authorUser.displayName}"
				icon = (authorUser.avatar ?: authorUser.defaultAvatar).url
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
				name = "$CHANNELS_EMOJI Salons :"
				value = channelsFormatted
			}
		}
	}
	
	companion object {
		private val verificationsChannelId = VERIF_CHANNEL
		
		suspend fun create(adMessage: Message) = Verification(
			author = adMessage.author!!.id,
			adContent = adMessage.content,
		).apply {
			val verificationChannel = bot.kord.getChannelOf<TextChannel>(verificationsChannelId)!!
			
			adChannels += VerificationMessage(adMessage.id, adMessage.channel.id)
			
			val verificationMessage = verificationChannel.createMessage {
				generateEmbed(adMessage)
				
				components {
					publicButton {
						emoji(kord.getRocketPubGuild().getEmoji(VALID_EMOJI))
						style = ButtonStyle.Success
						label = "Valider"
						
						useCallback(VALIDATE_VERIF_BUTTON_ID)
					}
					
					publicButton {
						emoji("\uD83D\uDDD1")
						style = ButtonStyle.Danger
						label = "Supprimer"
						
						useCallback(DELETE_ALL_ADS_VERIF_BUTTON_ID)
					}
				}
			}
			
			this.verificationMessage = verificationMessage
		}
	}
}

fun List<Verification>.findNotValidated(adMessage: Message) = find {
	it.adContent == adMessage.content && it.author == adMessage.author!!.id && !it.isValidated
}