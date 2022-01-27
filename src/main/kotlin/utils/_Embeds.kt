package utils

import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import configuration
import dev.kord.core.Kord
import dev.kord.core.entity.Embed
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import extensions.ModifyGuildValues
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import storage.BannedGuild
import storage.Sanction

suspend fun EmbedBuilder.basicEmbed(client: Kord) {
	val user = client.getSelf(EntitySupplyStrategy.cacheWithRestFallback)
	
	footer {
		icon = user.avatar?.url
		text = user.username
	}
	timestamp = Clock.System.now()
}

suspend fun EmbedBuilder.bannedGuildEmbed(client: Kord, guild: BannedGuild) {
	basicEmbed(client)
	
	title = "Serveur interdit."
	description = "Voici des informations sur ce serveur interdit."
	
	timestamp = Clock.System.now()
	field {
		name = "Nom/ID"
		value = if (guild.name == null) "ID: ${guild.id}" else "Nom: ${guild.name}"
		inline = true
	}
	
	field {
		name = "Depuis :"
		value = guild.bannedSince.toInstant().toKotlinInstant().toDiscord(TimestampType.LongDateTime)
	}
}

suspend fun EmbedBuilder.completeEmbed(client: Kord, title: String, description: String = "", block: EmbedBuilder.() -> Unit = {}) {
	basicEmbed(client)
	
	this.title = title
	if (description.isNotBlank()) this.description = description
	apply(block)
}

fun EmbedBuilder.fromEmbedUnlessChannelField(oldEmbed: Embed) {
	oldEmbed.fields.forEach {
		if (it.name == "Salons :") return@forEach
		field {
			name = it.name
			value = it.value
		}
	}
	
	description = oldEmbed.description
	title = oldEmbed.title
	url = oldEmbed.url
	timestamp = oldEmbed.timestamp
	color = oldEmbed.color
	
	if (oldEmbed.author != null) {
		author {
			name = oldEmbed.author!!.name
			icon = oldEmbed.author!!.iconUrl
			url = oldEmbed.author!!.url
		}
	}
	
	if (oldEmbed.footer != null) {
		footer {
			text = oldEmbed.footer!!.text
			icon = oldEmbed.footer!!.iconUrl
		}
	}
}

suspend fun EmbedBuilder.modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String
) {
	bannedGuildEmbed(client, guild)
	
	description = "Valeur `${value.translation}` modifiée.\nAvant:$valueBefore \nAprès:$valueAfter"
}

suspend fun EmbedBuilder.autoSanctionEmbed(event: MessageCreateEvent, sanction: Sanction) = autoSanctionEmbed(event, sanction, listOf(event.message.channel.asChannel()))

suspend fun EmbedBuilder.autoSanctionEmbed(
	event: MessageCreateEvent,
	sanction: Sanction,
	channels: List<Channel>
) {
	completeEmbed(
		event.kord,
		sanction.reason,
		sanction.toString(configuration["AYFRI_ROCKETMANAGER_PREFIX"])
	)
	
	url = event.message.getJumpUrl()
	
	footer {
		text = "Cliquez sur le titre de l'embed pour aller sur le message."
	}
	
	field {
		name = "<:moderator:933507900092072046> Par :"
		value = "${event.member!!.tag} (`${sanction.member}`)"
	}
	
	field {
		name = "<:textuel:658085848092508220> Salons :"
		value = channels.joinToString("\n", transform = Channel::mention)
	}
}

suspend fun EmbedBuilder.sanctionEmbed(kord: Kord, sanction: Sanction) {
	val user = kord.getUser(sanction.member)!!
	
	completeEmbed(
		kord,
		"${sanction.type.emote}${sanction.type.translation} de ${user.tag}",
		"Nouvelle sanction appliquée à ${user.mention} (`${user.id}`)."
	)
	field {
		name = "\uD83D\uDCC4 Raison :"
		value = sanction.reason
	}
	
	if (sanction.appliedBy != null) {
		field {
			name = "<:moderator:933507900092072046> Par :"
			value = "${kord.getUser(sanction.appliedBy)?.tag} (`${sanction.appliedBy}`)"
		}
	}
	
	if (sanction.durationMS != 0L) {
		field {
			name = ":clock1: Durée :"
			value = sanction.formattedDuration
		}
	}
}

suspend fun EmbedBuilder.verificationEmbed(
	event: MessageCreateEvent,
	channels: MutableSet<TextChannel>
) {
	completeEmbed(event.kord, "Nouvelle publicité à vérifier.", event.message.content)
	val link = findInviteCode(event.message.content)
	
	field {
		name = "<:textuel:658085848092508220> Salons :"
		value = channels.joinToString("\n", transform = TextChannel::mention)
	}
	
	if (link != null) {
		try {
			val invite = getInvite(event.kord, link)
			val guild = invite?.partialGuild?.getGuild()
			
			field {
				name = "Invitation :"
				value = """
				Serveur : ${invite?.partialGuild?.name ?: "Non trouvé."}
				ID du serveur : ${invite?.partialGuild?.id?.toString() ?: "Non trouvé."}
				Nombre de membres : ${guild?.memberCount ?: invite?.approximateMemberCount ?: "Non trouvé."}
				Owner : ${
					when {
						invite?.partialGuild?.owner == true -> "${event.member?.mention} (`${event.member?.id}`)"
						guild?.owner != null -> "${guild.owner.mention} (`${guild.ownerId}`)"
						else -> "Non trouvé."
					}
				}
				""".trimIndent()
			}
		} catch (_: Exception) {
			field {
				name = "Invitation :"
				value = findInviteLink(event.message.content)!!
			}
		}
	}
	
	field {
		name = "<:user:933508955722899477> Par :"
		value = "${event.message.author!!.mention} (`${event.message.author!!.id}`)"
	}
}

suspend fun FollowupMessageCreateBuilder.completeEmbed(client: Kord, title: String, description: String, block: EmbedBuilder.() -> Unit = {}) = embed { completeEmbed(client, title, description) }

suspend fun FollowupMessageCreateBuilder.bannedGuildEmbed(client: Kord, guild: BannedGuild) = embed { bannedGuildEmbed(client, guild) }

suspend fun FollowupMessageCreateBuilder.modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String
) = embed { modifiedGuildEmbed(client, guild, value, valueBefore, valueAfter) }

suspend fun FollowupMessageCreateBuilder.sanctionEmbed(kord: Kord, sanction: Sanction) {
	embed {
		val user = kord.getUser(sanction.member)!!
		
		completeEmbed(
			kord,
			"${sanction.type.emote}${sanction.type.translation} de ${user.tag}",
			"Nouvelle sanction appliquée à ${user.mention} (`${user.id}`)."
		)
		
		field {
			name = "\uD83D\uDCC4 Raison :"
			value = sanction.reason
		}
		
		if (sanction.appliedBy != null) {
			field {
				name = "<:moderator:933507900092072046> Par :"
				value = "${kord.getUser(sanction.appliedBy)?.tag} (`${sanction.appliedBy}`)"
			}
		}
		
		if (sanction.durationMS != 0L) {
			field {
				name = ":clock1: Durée :"
				value = sanction.formattedDuration
			}
		}
	}
}
