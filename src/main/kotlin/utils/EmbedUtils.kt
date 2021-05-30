package utils

import com.kotlindiscord.kord.extensions.utils.getUrl
import configuration
import dev.kord.core.Kord
import dev.kord.core.entity.Embed
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.EmbedBuilder
import extensions.ModifyGuildValues
import storage.BannedGuild
import storage.Sanction
import java.text.SimpleDateFormat
import java.time.Instant

suspend fun basicEmbed(client: Kord): suspend EmbedBuilder.() -> Unit = {
	val user = client.getSelf(EntitySupplyStrategy.cacheWithRestFallback)
	
	footer {
		icon = user.avatar.url
		text = user.username
	}
	timestamp = Instant.now()
}

suspend fun completeEmbed(client: Kord, title: String, description: String): suspend EmbedBuilder.() -> Unit = {
	basicEmbed(client)()
	
	this.title = title
	this.description = description
}

suspend fun sanctionEmbed(event: MessageCreateEvent, sanction: Sanction): suspend EmbedBuilder.() -> Unit = {
	sanctionEmbed(event, sanction, listOf(event.message.channel.asChannel()))()
}

suspend fun sanctionEmbed(
	event: MessageCreateEvent,
	sanction: Sanction,
	channels: List<Channel>
): suspend EmbedBuilder.() -> Unit = {
	completeEmbed(
		event.kord,
		sanction.reason,
		sanction.toString(configuration["AYFRI_ROCKETMANAGER_PREFIX"])
	)()
	
	url = event.message.getUrl()
	
	footer {
		text = "Cliquez sur le titre de l'embed pour aller sur le message."
	}
	
	field {
		name = "Par :"
		value = "${event.member!!.tag} (`${sanction.member.asString}`)"
	}
	
	field {
		name = "Salons :"
		value = channels.joinToString("\n") { it.mention }
	}
}

suspend fun bannedGuildEmbed(client: Kord, guild: BannedGuild): suspend EmbedBuilder.() -> Unit = {
	basicEmbed(client)()
	
	title = "Serveur interdit."
	description = "Voici des informations sur ce serveur interdit."
	
	timestamp = Instant.now()
	field {
		name = "Nom/ID"
		value = if (guild.name == null) "ID: ${guild.id}" else "Nom: ${guild.name}"
		inline = true
	}
	
	field {
		name = "Depuis :"
		value = SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(guild.bannedSince)
	}
}

suspend fun modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String
): suspend EmbedBuilder.() -> Unit = {
	bannedGuildEmbed(client, guild)()
	
	description = "Valeur `${value.translation}` modifiée.\nAvant:$valueBefore \nAprès:$valueAfter"
}

suspend fun verificationEmbed(
	event: MessageCreateEvent,
	channels: MutableSet<TextChannel>
): suspend EmbedBuilder.() -> Unit = {
	completeEmbed(event.kord, "Nouvelle publicité à vérifier.", event.message.content)()
	val link = findInviteCode(event.message.content)
	
	field {
		name = "Salons :"
		value = channels.joinToString("\n") { it.mention }
	}
	
	if (link != null) {
		try {
			val invite = getInvite(event.kord, link)
			val guild = invite?.partialGuild?.getGuild()
			
			field {
				name = "Invitation :"
				value = """
				Serveur: ${invite?.partialGuild?.name ?: "Non trouvé."}
				ID du serveur : ${invite?.partialGuild?.id?.asString ?: "Non trouvé."}
				Nombre de membres : ${guild?.memberCount ?: invite?.approximateMemberCount ?: "Non trouvé."}
				Owner : ${
					when {
						invite?.partialGuild?.owner == true -> "${event.member?.mention} (`${event.member?.id}`)"
						guild?.owner != null -> "${guild.owner.mention} (`${guild.ownerId.asString}`)"
						else -> "Non trouvé."
					}
				}
				""".trimIndent()
			}
		} catch (ignore: Exception) {
			field {
				name = "Invitation :"
				value = findInviteLink(event.message.content)!!
			}
		}
	}
	
	field {
		name = "Par :"
		value = "${event.message.author!!.mention} (`${event.message.author!!.id.asString}`)"
	}
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
