package extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.channelType
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.addReaction
import com.kotlindiscord.kord.extensions.utils.delete
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Invite
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import storage.Sanction
import storage.SanctionType
import utils.forChannel
import utils.fromEmbedUnlessFields
import utils.getFromValue
import utils.removeMatches
import utils.sanctionEmbed
import utils.searchBannedGuild
import kotlin.time.ExperimentalTime
import kotlin.time.days
import kotlin.time.minutes

suspend fun isInAdChannel(event: MessageCreateEvent): Boolean {
	val channel = event.message.channel.asChannel()
	return (channel is TextChannel
		&& channel.topic != null
		&& channel.topic!!.contains(VALIDATION_EMOJI))
}

suspend fun isInCategoryAdChannel(event: MessageCreateEvent): Boolean {
	val channel = event.message.channel.asChannel()
	return (channel is TextChannel
		&& channel.topic != null
		&& channel.topic!!.contains(VALIDATION_EMOJI_2))
}

suspend fun getLogChannel(event: MessageCreateEvent): TextChannel {
	return event.getGuild()!!.channels.first { it.id == SANCTION_LOGGER_CHANNEL } as TextChannel
}

fun isNotBot(event: MessageCreateEvent): Boolean = if (event.member != null) !event.member!!.isBot else false

suspend fun getChannelsFromSanctionMessage(message: Message, bot: ExtensibleBot): MutableSet<MessageChannel> {
	val embed = message.embeds[0]
	val field = embed.fields.find { it.name == "Salons" }
	return field!!.value.split(Regex("\n")).mapNotNull {
		val id = Snowflake.forChannel(it)
		bot.getKoin().get<Kord>().getChannel(id) as MessageChannel?
	}.toMutableSet()
}

const val DISCORD_INVITE_LINK_REGEX = "(?:https?:\\/\\/)?(?:\\w+\\.)?discord(?:(?:app)?\\.com\\/invite|\\.gg)\\/([A-Za-z0-9-]+)"
const val VALIDATION_EMOJI_2 = "🔗"
const val VALIDATION_EMOJI = "<:validate:525405975289659402>"
val ROCKET_PUB_GUILD = Snowflake("465918902254436362")
val STAFF_ROLE = Snowflake("494521544618278934")
val SANCTION_LOGGER_CHANNEL = Snowflake("779115065001115649")

data class SanctionMessage(val member: Member, var sanctionMessage: Message, val sanction: Sanction)

@OptIn(ExperimentalTime::class, KordPreview::class)
class CheckAds : Extension() {
	override val name = "CheckAds"
	val sanctionMessages = mutableListOf<SanctionMessage>()
	
	suspend fun getInvite(text: String): Invite? = bot.getKoin().get<Kord>().getInvite(text, false)
	
	fun findInviteLink(text: String): String? = Regex(DISCORD_INVITE_LINK_REGEX).find(text)?.value
	
	suspend fun createSanction(event: MessageCreateEvent, type: SanctionType, reason: String?) {
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
			val message = getLogChannel(event).createMessage {
				embed { sanctionEmbed(event, sanction)() }
			}
			message.addReaction("\uD83D\uDDD1️")
			
			val liveMessage = message.live()
			liveMessage.onReactionAdd { reactionEvent ->
				if (reactionEvent.getUser().isBot) return@onReactionAdd
				
				val channels = getChannelsFromSanctionMessage(message, bot)
				channels.forEach {
					it.getMessagesBefore(it.getLastMessage()!!.id).firstOrNull { findMessage ->
						sanctionMessages.find { sanctionMessage ->
							sanctionMessage.sanction.reason == getReasonForMessage(findMessage) &&
								sanctionMessage.member.id == findMessage.author!!.id
						} != null
					}?.delete()
				}
			}
			
			sanctionMessages.add(SanctionMessage(event.member!!, message, sanction))
		}
		
		event.message.delete(5.minutes.toLongMilliseconds())
	}
	
	override suspend fun setup() {
		event<MessageDeleteEvent> {
			check(inGuild(ROCKET_PUB_GUILD), channelType(ChannelType.GuildText), ::isNotBot, ::isInAdChannel)
			
			action {
				val content = event.message?.content ?: return@action
				val mention = Regex("@(everyone|here)").find(content)
				
				val reason = when {
					!Regex("\\s").containsMatchIn(content) -> "Publicité sans description."
					mention != null -> "Tentative de mention ${mention.value}."
					content == "test" -> "Test."
					else -> null
				}
				
				if (reason != null) {
					val old = sanctionMessages.find {
						it.sanction.member == event.message!!.author!!.id
							&& it.sanction.reason == reason
					}
					
					if (old != null) {
						val oldEmbed = old.sanctionMessage.embeds[0]
						val channels = oldEmbed.fields.find { it.name == "Salons" }!!.value.split(Regex("\n")).toMutableList()
						val find = channels.find { it == event.channel.mention } ?: return@action
						
						channels.remove(find)
						channels.add(find.plus(" (supprimé)"))
						
						sanctionMessages.getFromValue(old).sanctionMessage = old.sanctionMessage.edit {
							embed {
								fromEmbedUnlessFields(oldEmbed)
								
								field {
									name = "Utilisateur incriminé :"
									value = "${event.message?.author?.tag} (`${event.message?.author?.id?.asString}`)"
								}
								
								field {
									name = "Salons"
									value = channels.joinToString("\n")
								}
							}
						}
					}
				}
			}
		}
		
		event<MessageCreateEvent> {
			check(
				channelType(ChannelType.GuildText),
				inGuild(ROCKET_PUB_GUILD),
				/*hasRole(STAFF_ROLE),*/
				::isInAdChannel,
				::isNotBot
			)
			
			action {
				createSanction(event, SanctionType.WARN, getReasonForMessage(event.message))
			}
		}
	}
	
	suspend fun getReasonForMessage(message: Message): String? {
		val mention = Regex("@(everyone|here)").find(message.content)
		val inviteLink = findInviteLink(message.content)
		val invite = if (inviteLink != null) getInvite(inviteLink) else null
		
		val isBannedGuild = invite != null &&
			(
				invite.partialGuild?.id?.let { searchBannedGuild(it) } != null ||
					invite.partialGuild?.name?.let { searchBannedGuild(it) } != null
				)
		
		return when {
			!Regex("\\s").containsMatchIn(message.content) -> "Publicité sans description."
			mention != null -> "Tentative de mention `${mention.value.removeMatches("@")}`."
			message.content == "test" -> "Test."
			isBannedGuild -> "Publicité pour un serveur interdit."
			else -> null
		}
	}
}
