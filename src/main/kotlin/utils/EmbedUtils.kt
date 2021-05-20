package utils

import com.kotlindiscord.kord.extensions.utils.getUrl
import configuration
import dev.kord.core.Kord
import dev.kord.core.entity.Embed
import dev.kord.core.entity.channel.Channel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.EmbedBuilder
import extensions.ModifyGuildValues
import storage.BannedGuild
import storage.Sanction
import java.text.SimpleDateFormat
import java.time.Instant

suspend fun templateBasic(client: Kord): suspend EmbedBuilder.() -> Unit = {
	val user = client.getSelf(EntitySupplyStrategy.cacheWithRestFallback)
	
	footer {
		icon = user.avatar.url
		text = user.username
	}
	timestamp = Instant.now()
}

suspend fun templateComplete(client: Kord, title: String, description: String): suspend EmbedBuilder.() -> Unit = {
	templateBasic(client)()
	
	this.title = title
	this.description = description
}

suspend fun templateSanction(event: MessageCreateEvent, sanction: Sanction): suspend EmbedBuilder.() -> Unit = {
	templateSanction(event, sanction, listOf(event.message.channel.asChannel()))()
}

suspend fun templateSanction(
	event: MessageCreateEvent,
	sanction: Sanction,
	channels: List<Channel>
): suspend EmbedBuilder.() -> Unit = {
	templateComplete(
		event.kord,
		sanction.reason,
		sanction.toString(configuration["PREFIX"])
	)()
	
	url = event.message.getUrl()
	
	footer {
		text = "Cliquez sur le titre de l'embed pour aller sur le message."
	}
	
	field {
		name = "Utilisateur incriminé :"
		value = "${event.member!!.tag} (`${sanction.member.asString}`)"
	}
	
	field {
		name = "Salons"
		value = channels.joinToString("\n") { it.mention }
	}
}

suspend fun templateBannedGuild(client: Kord, guild: BannedGuild): suspend EmbedBuilder.() -> Unit = {
	templateBasic(client)()
	
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

suspend fun templateModifiedGuild(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String
): suspend EmbedBuilder.() -> Unit = {
	templateBannedGuild(client, guild)()
	
	description = "Valeur `${value.translation}` modifiée.\nAvant:$valueBefore \nAprès:$valueAfter"
}

fun EmbedBuilder.fromEmbedUnlessFields(oldEmbed: Embed) {
	description = oldEmbed.description
	title = oldEmbed.description
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
