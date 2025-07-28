package utils

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
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kordex.core.utils.getJumpUrl
import extensions.ModifyGuildValues
import fr.ayfri.rocketmanager.i18n.Translations
import storage.BannedGuild
import storage.Sanction
import kotlin.time.Clock
import kotlin.time.toKotlinInstant

suspend fun EmbedBuilder.autoSanctionEmbed(
	message: Message,
	sanction: Sanction,
	messages: List<Message> = listOf(message),
) {
	completeEmbed(
		message.kord,
		sanction.reason,
		sanction.toString(System.getenv("AYFRI_ROCKETMANAGER_PREFIX"))
	)

	url = message.getJumpUrl()

	footer {
		text = Translations.Embeds.autoSanctionFooter.translate()
	}

	field {
		name = Translations.Embeds.autoSanctionAppliedBy.translate()
		value = "${message.author!!.username} (`${sanction.member}`)"
	}

	field {
		name = Translations.Embeds.autoSanctionMessages.translate()
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

	title = Translations.Embeds.BannedGuild.title.translate()
	description = Translations.Embeds.BannedGuild.description.translate()

	timestamp = Clock.System.now()

	field {
		name = Translations.Fields.reason.translate()
		value = guild.reason
	}

	field {
		name = Translations.Fields.nameId.translate()
		value =
			if (guild.name == null) Translations.Fields.idValue.translateNamed("id" to guild.id) else Translations.Fields.nameValue.translateNamed(
				"name" to guild.name
			)
		inline = true
	}

	field {
		name = Translations.Fields.since.translate()
		value = guild.bannedSince.toInstant().toKotlinInstant().toMessageFormat(DiscordTimestampStyle.LongDateTime)
	}
}

suspend fun EmbedBuilder.completeEmbed(
	client: Kord,
	title: String,
	description: String = "",
	block: EmbedBuilder.() -> Unit = {}
) {
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

	description = Translations.Embeds.EndAdChannel.description.translate()
}

suspend fun EmbedBuilder.modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String,
) {
	bannedGuildEmbed(client, guild)

	description = Translations.Embeds.ModifiedGuild.description.translateNamed(
		"value" to value.translation,
		"before" to valueBefore,
		"after" to valueAfter
	)
}

suspend fun EmbedBuilder.sanctionEmbed(kord: Kord, sanction: Sanction) {
	val user = kord.getUser(sanction.member)!!

	completeEmbed(
		kord,
		Translations.Embeds.Sanction.title.translateNamed(
			"emote" to sanction.type.emote,
			"type" to sanction.type.translation,
			"username" to user.username
		),
		Translations.Embeds.Sanction.description.translateNamed(
			"user" to user.mention,
			"id" to user.id.toString()
		)
	)

	field {
		name = Translations.Fields.reason.translate()
		value = sanction.reason
	}

	if (sanction.appliedBy != null) {
		field {
			name = Translations.Fields.appliedBy.translate()
			value =
				if (sanction.appliedBy == kord.selfId) Translations.Messages.botOrDiscordInterface.translate()
				else "${kord.getUser(sanction.appliedBy)?.username} (`${sanction.appliedBy}`)"
		}
	}

	if (sanction.durationMS != 0L) {
		field {
			name = Translations.Fields.duration.translate()
			value = sanction.formattedDuration
		}
	}
}

suspend fun EmbedBuilder.unBanEmbed(
	kord: Kord,
	user: UserBehavior,
	unBannedBy: UserBehavior? = null,
	reason: String? = null
) {
	completeEmbed(
		kord,
		Translations.Embeds.UnBan.title.translateNamed("id" to user.id.toString())
	)

	if (unBannedBy != null) {
		field {
			val moderator = unBannedBy.fetchUserOrNull() ?: return@field
			name = Translations.Fields.appliedBy.translate()
			value = "${moderator.username} (`${moderator.id}`)"
		}
	}

	if (reason != null) {
		field {
			name = Translations.Fields.reason.translate()
			value = reason
		}
	}
}

suspend fun EmbedBuilder.unMuteEmbed(kord: Kord, user: UserBehavior, unMutedBy: UserBehavior? = null) {
	completeEmbed(
		kord,
		Translations.Embeds.UnMute.title.translateNamed("id" to user.id.toString()),
		Translations.Embeds.UnMute.description.translateNamed(
			"user" to user.mention,
			"id" to user.id.toString()
		)
	)

	if (unMutedBy != null) {
		field {
			val moderator = unMutedBy.fetchUserOrNull() ?: return@field
			name = Translations.Fields.appliedBy.translate()
			value =
				if (unMutedBy.id == kord.selfId) Translations.Messages.botOrDiscordInterface.translate()
				else "${moderator.username} (`${moderator.id}`)"
		}
	}
}

suspend fun MessageCreateBuilder.completeEmbed(
	client: Kord,
	title: String,
	description: String,
	block: EmbedBuilder.() -> Unit = {}
) =
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

suspend fun MessageModifyBuilder.completeEmbed(
	client: Kord,
	title: String,
	description: String,
	block: EmbedBuilder.() -> Unit = {}
) =
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
