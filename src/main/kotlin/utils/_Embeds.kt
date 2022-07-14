package utils

import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import configuration
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import dev.kord.core.Kord
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.entity.Embed
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import extensions.ModifyGuildValues
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import storage.BannedGuild
import storage.Sanction

suspend fun EmbedBuilder.autoSanctionEmbed(
	message: Message,
	sanction: Sanction,
	channels: List<ChannelBehavior> = listOf(message.channel)
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
		value = "${message.author!!.tag} (`${sanction.member}`)"
	}
	
	field {
		name = "<:textuel:658085848092508220> Salons :"
		value = channels.joinToString("\n", transform = ChannelBehavior::mention)
	}
}

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

fun EmbedBuilder.fromEmbedUnlessChannelField(oldEmbed: Embed) {
	oldEmbed.fields.forEach {
		if (it.name.endsWith("Salons :")) return@forEach
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

suspend fun EmbedBuilder.endAdChannelEmbed(client: Kord, channel: TextChannel) {
	basicEmbed(client)
	
	author {
		name = channel.getGuild().name
		icon = channel.getGuild().getIconUrl(Image.Format.GIF)
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
	valueAfter: String
) {
	bannedGuildEmbed(client, guild)
	
	description = "Valeur `${value.translation}` modifiée.\nAvant:$valueBefore \nAprès:$valueAfter"
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
			value = if (sanction.appliedBy == kord.selfId) "Par le bot ou depuis l'interface discord (membre non récupérable)."
			else "${kord.getUser(sanction.appliedBy)?.tag} (`${sanction.appliedBy}`)"
		}
	}
	
	if (sanction.durationMS != 0L) {
		field {
			name = ":clock1: Durée :"
			value = sanction.formattedDuration
		}
	}
}

@OptIn(PrivilegedIntent::class)
suspend fun EmbedBuilder.verificationEmbed(
	message: Message,
	vararg channels: TextChannel,
) {
	completeEmbed(message.kord, "Nouvelle publicité à vérifier.", message.content)
	val link = findInviteCode(message.content)
	
	field {
		name = "<:textuel:658085848092508220> Salons :"
		value = channels.distinctBy { it.id.value }.joinToString("\n", transform = TextChannel::mention)
	}
	
	if (link != null) {
//		try {
		val invite = getInvite(message.kord, link)
		println(invite.prettyPrint())
		val guild: Guild? = try {
			invite?.partialGuild?.getGuildOrNull()
		} catch (_: Exception) {
			null
		}
		
		val owner = guild?.getOwnerOrNull() ?: try {
			invite?.partialGuild?.members?.firstOrNull {
				it.isOwner()
			}
		} catch (_: Exception) {
			null
		} ?: invite?.partialGuild?.owner?.let { inviterIsOwner ->
			if (!inviterIsOwner) return@let null
			
			invite.inviterId?.let {
				message.kord.getGuild(ROCKET_PUB_GUILD)?.getMemberOrNull(it)
			}
		}
		
		field {
			name = "Invitation :"
			value = """
				Serveur : ${invite?.partialGuild?.name ?: "Non trouvé."}
				ID du serveur : ${invite?.partialGuild?.id?.toString() ?: "Non trouvé."}
				Nombre de membres : ${guild?.memberCount ?: invite?.approximateMemberCount ?: "Non trouvé."}
				Owner : ${
				when {
					invite?.partialGuild?.owner == true -> "${message.author?.mention} (`${message.author?.id}`)"
					owner != null -> "${owner.mention} (`${owner.id}`)"
					else -> "Non trouvé."
				}
			}
			""".trimIndent()
		}
/*		} catch (e: Exception) {
			e.printStackTrace()
			
			field {
				name = "Invitation :"
				value = findInviteLink(message.content)!!
			}
		}*/
	}
	
	field {
		val user = message.author?.fetchUserOrNull() ?: message.kord.getSelf(EntitySupplyStrategy.cacheWithCachingRestFallback)
		name = "<:user:933508955722899477> Par :"
		value = "${user.mention} (`${user.id}`)"
	}
}

suspend fun EmbedBuilder.unBanEmbed(kord: Kord, user: UserBehavior, unBannedBy: UserBehavior? = null) {
	completeEmbed(
		kord,
		"Dé-banissement de ${user.id}"
	)
	
	if (unBannedBy != null) {
		field {
			val moderator = unBannedBy.fetchUserOrNull() ?: return@field
			name = "<:moderator:933507900092072046> Par :"
			value = if (unBannedBy.id == kord.selfId) "Par le bot ou depuis l'interface discord (membre non récupérable)."
			else "${moderator.tag} (`${moderator.id}`)"
		}
	}
}

suspend fun EmbedBuilder.unMuteEmbed(kord: Kord, user: UserBehavior, unMutedBy: UserBehavior? = null) {
	completeEmbed(
		kord,
		"Dé-mute de ${user.id}",
		"Le membre ${user.mention} (`${user.id}`) a bien été dé-mute."
	)
	
	if (unMutedBy != null) {
		field {
			val moderator = unMutedBy.fetchUserOrNull() ?: return@field
			name = "<:moderator:933507900092072046> Par :"
			value = if (unMutedBy.id == kord.selfId) "Par le bot ou depuis l'interface discord (membre non récupérable)."
			else "${moderator.tag} (`${moderator.id}`)"
		}
	}
}

suspend fun MessageCreateBuilder.completeEmbed(client: Kord, title: String, description: String, block: EmbedBuilder.() -> Unit = {}) =
	embed { completeEmbed(client, title, description, block) }

suspend fun MessageCreateBuilder.bannedGuildEmbed(client: Kord, guild: BannedGuild) = embed { bannedGuildEmbed(client, guild) }

suspend fun MessageCreateBuilder.modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String
) = embed { modifiedGuildEmbed(client, guild, value, valueBefore, valueAfter) }

suspend fun MessageCreateBuilder.sanctionEmbed(kord: Kord, sanction: Sanction) {
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

suspend fun MessageModifyBuilder.completeEmbed(client: Kord, title: String, description: String, block: EmbedBuilder.() -> Unit = {}) =
	embed { completeEmbed(client, title, description, block) }

suspend fun MessageModifyBuilder.bannedGuildEmbed(client: Kord, guild: BannedGuild) = embed { bannedGuildEmbed(client, guild) }

suspend fun MessageModifyBuilder.modifiedGuildEmbed(
	client: Kord,
	guild: BannedGuild,
	value: ModifyGuildValues,
	valueBefore: String,
	valueAfter: String
) = embed { modifiedGuildEmbed(client, guild, value, valueBefore, valueAfter) }

suspend fun MessageModifyBuilder.sanctionEmbed(kord: Kord, sanction: Sanction) {
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