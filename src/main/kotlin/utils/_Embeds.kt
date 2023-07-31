package utils

import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import configuration
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import dev.kord.core.Kord
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.entity.Embed
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.Image
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import extensions.ModifyGuildValues
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import storage.BannedGuild
import storage.Sanction

suspend fun EmbedBuilder.autoSanctionEmbed(
	message: Message,
	sanction: Sanction,
	messages: List<Message> = listOf(message),
) {
	completeEmbed(
		message.kord,
		sanction.reason,
		sanction.toString(configuration["AYFRI_ROCKETMANAGER_PREFIX"])
	)

	url = message.getJumpUrl()

	footer {
		text = "Cliquez sur le titre de l'embed pour aller sur le message."
	}

	field {
		name = "<:moderator:933507900092072046> Par :"
		value = "${message.author!!.username} (`${sanction.member}`)"
	}

	field {
		name = "<:textuel:658085848092508220> Messages :"
		value = messages.joinToString("\n", transform = Message::getJumpUrl)
	}
}

suspend fun EmbedBuilder.basicEmbed(client: Kord) {
	val user = client.getSelf(EntitySupplyStrategy.cacheWithRestFallback)

	footer {
		icon = user.avatar?.cdnUrl?.toUrl { size = Image.Size.Size512 }
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
		name = "Raison :"
		value = guild.reason
	}

	field {
		name = "Nom/ID :"
		value = if (guild.name == null) "ID: ${guild.id}" else "Nom: ${guild.name}"
		inline = true
	}

	field {
		name = "Depuis :"
		value = guild.bannedSince.toInstant().toKotlinInstant().toMessageFormat(DiscordTimestampStyle.LongDateTime)
	}
}

suspend fun EmbedBuilder.completeEmbed(client: Kord, title: String, description: String = "", block: EmbedBuilder.() -> Unit = {}) {
	basicEmbed(client)

	this.title = title
	if (description.isNotBlank()) this.description = description
	apply(block)
}

fun EmbedBuilder.fromEmbed(oldEmbed: Embed) {
	oldEmbed.fields.forEach {
		field(it.name, it.inline ?: false) { it.value }
	}

	description = oldEmbed.description
	title = oldEmbed.title
	url = oldEmbed.url
	timestamp = oldEmbed.timestamp
	color = oldEmbed.color
	image = oldEmbed.image?.url

	oldEmbed.thumbnail?.let {
		thumbnail {
			url = it.url!!
		}
	}

	oldEmbed.author?.let {
		author {
			name = it.name
			icon = it.iconUrl
			url = it.url
		}
	}

	oldEmbed.footer?.let {
		footer {
			text = it.text
			icon = it.iconUrl
		}
	}
}

suspend fun EmbedBuilder.endAdChannelEmbed(client: Kord, channel: TextChannel) {
	basicEmbed(client)

	author {
		name = channel.getGuild().name
		icon = channel.getGuild().icon?.cdnUrl?.toUrl { format = Image.Format.GIF }
	}

	description = """**
			📌 Votre publicité doit respecter les ToS de Discord.
			<:textuel:658085848092508220> Slowmode de 1h maximum !!
			<a:girorouge:525406076057944096> Si vous quittez le serveur vos publicités seront supprimée automatiquement !
		**""".trimIndent()

}

suspend fun EmbedBuilder.modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String,
) {
	bannedGuildEmbed(client, guild)

	description = "Valeur `${value.translation}` modifiée.\nAvant:$valueBefore \nAprès:$valueAfter"
}

suspend fun EmbedBuilder.sanctionEmbed(kord: Kord, sanction: Sanction) {
	val user = kord.getUser(sanction.member)!!

	completeEmbed(
		kord,
		"${sanction.type.emote}\n${sanction.type.translation} de ${user.username}",
		"Nouvelle sanction appliquée à ${user.mention} (`${user.id}`)."
	)

	field {
		name = "\uD83D\uDCC4 Raison :"
		value = sanction.reason
	}

	if (sanction.appliedBy != null) {
		field {
			name = "<:moderator:933507900092072046> Par :"
			value = if (sanction.appliedBy == kord.selfId) "Par le bot ou depuis l'interface discord (membre non récupérable)."
			else "${kord.getUser(sanction.appliedBy)?.username} (`${sanction.appliedBy}`)"
		}
	}

	if (sanction.durationMS != 0L) {
		field {
			name = ":clock1: Durée :"
			value = sanction.formattedDuration
		}
	}
}

suspend fun EmbedBuilder.unBanEmbed(kord: Kord, user: UserBehavior, unBannedBy: UserBehavior? = null, reason: String? = null) {
	completeEmbed(
		kord,
		"🔓\nDé-bannissement de ${user.id}"
	)

	if (unBannedBy != null) {
		field {
			val moderator = unBannedBy.fetchUserOrNull() ?: return@field
			name = "<:moderator:933507900092072046> Par :"
			value = "${moderator.username} (`${moderator.id}`)"
		}
	}

	if (reason != null) {
		field {
			name = "\uD83D\uDCC4 Raison :"
			value = reason
		}
	}
}

suspend fun EmbedBuilder.unMuteEmbed(kord: Kord, user: UserBehavior, unMutedBy: UserBehavior? = null) {
	completeEmbed(
		kord,
		"🔉 Dé-mute de ${user.id}",
		"Le membre ${user.mention} (`${user.id}`) a bien été dé-mute."
	)

	if (unMutedBy != null) {
		field {
			val moderator = unMutedBy.fetchUserOrNull() ?: return@field
			name = "<:moderator:933507900092072046> Par :"
			value = if (unMutedBy.id == kord.selfId) "Par le bot ou depuis l'interface discord (membre non récupérable)."
			else "${moderator.username} (`${moderator.id}`)"
		}
	}
}

suspend fun MessageCreateBuilder.completeEmbed(client: Kord, title: String, description: String, block: EmbedBuilder.() -> Unit = {}) =
	embed {
		completeEmbed(client, title, description, block)
	}

suspend fun MessageCreateBuilder.bannedGuildEmbed(client: Kord, guild: BannedGuild) = embed {
	bannedGuildEmbed(client, guild)
}

suspend fun MessageCreateBuilder.modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String,
) = embed {
	modifiedGuildEmbed(client, guild, value, valueBefore, valueAfter)
}

suspend fun MessageCreateBuilder.sanctionEmbed(kord: Kord, sanction: Sanction) = embed {
	sanctionEmbed(kord, sanction)
}

suspend fun MessageModifyBuilder.completeEmbed(client: Kord, title: String, description: String, block: EmbedBuilder.() -> Unit = {}) =
	embed {
		completeEmbed(client, title, description, block)
	}

suspend fun MessageModifyBuilder.bannedGuildEmbed(client: Kord, guild: BannedGuild) = embed {
	bannedGuildEmbed(client, guild)
}

suspend fun MessageModifyBuilder.modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String,
) = embed {
	modifiedGuildEmbed(client, guild, value, valueBefore, valueAfter)
}

suspend fun MessageModifyBuilder.sanctionEmbed(kord: Kord, sanction: Sanction) = embed {
	sanctionEmbed(kord, sanction)
}
