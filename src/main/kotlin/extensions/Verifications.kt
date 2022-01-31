package extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.message
import com.kotlindiscord.kord.extensions.components.ComponentContainer
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.components.publicSelectMenu
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralMessageCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.emoji
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.create.embed
import storage.Sanction
import storage.SanctionType
import storage.getVerificationCount
import utils.ROCKET_PUB_GUILD
import utils.STAFF_ROLE
import utils.SanctionMessage
import utils.VALID_EMOJI
import utils.autoSanctionEmbed
import utils.completeEmbed
import utils.getReasonForMessage
import utils.verificationEmbed

@OptIn(KordPreview::class)
class Verifications : Extension() {
	override val name = "Verifications"
	
	class VerifyMessageArguments : Arguments() {
		val message by message {
			name = "message"
			description = "Le message à verifier."
			requireGuild = true
		}
	}
	
	override suspend fun setup() {
		publicSlashCommand {
			name = "verif"
			description = "Permet de voir les vérifications du staff."
			
			check { isStaff() }
			
			publicSubCommand {
				name = "list"
				description = "Permet de voir les vérifications du staff."
				
				action {
					val verificationCount = getVerificationCount()
					val verifications = verificationCount.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
					
					respond {
						completeEmbed(
							this@publicSubCommand.kord, "Liste des publicités vérifiées.", verifications.map {
								"**${guild!!.getMember(it.first).tag}** : ${it.second} publicités vérifiées."
							}.joinToString("\n\n")
						)
					}
				}
			}
			
			publicSubCommand(::VerifyMessageArguments) {
				name = "message"
				description = "Permet de vérifier un message."
				
				action {
					val message = arguments.message.fetchMessage()
					
					getReasonForMessage(message)?.let {
						autoSanctionMessage(message, SanctionType.WARN, it)
					} ?: verificationMessage(message)
				}
			}
		}
		
		ephemeralMessageCommand {
			name = "pub-interdite"
			allowRole(STAFF_ROLE)
			guild(ROCKET_PUB_GUILD)
			
			action {
				val type = user.getNextSanctionType()
				val message = targetMessages.elementAt(0)
				
				val author = message.getAuthorAsMember()!!
				message.delete("Publicité interdite.")
				Sanction(type, "Publicité interdite.", author.id, user.fetchUserOrNull()?.id, if (type == SanctionType.MUTE) author.getNextMuteDuration() else 0).apply {
					save()
					sendLog(message.kord)
					
					respond {
						applyToMember(author)
						content = "${author.mention} a été sanctionné pour avoir publié une publicité interdite."
					}
				}
			}
		}
	}
}

suspend fun ComponentContainer.addBinButtonDeleteSimilarAds() {
	publicButton {
		emoji("\uD83D\uDDD1️")
		label = "Supprimer"
		action {
			message?.let {
				deleteAllSimilarAds(it)
				it.edit {
					components = mutableListOf()
				}
			}
		}
	}
}

suspend fun ComponentContainer.addVerificationButton() {
	publicButton {
		emoji(kord.getGuild(ROCKET_PUB_GUILD)!!.getEmoji(VALID_EMOJI))
		style = ButtonStyle.Success
		label = "Valider"
		action {
			message?.let {
				validate(it, user)
				it.edit {
					components = mutableListOf()
				}
			}
		}
	}
}

suspend fun PublicSlashCommandContext<*>.autoSanctionMessage(sanctionMessage: Message, type: SanctionType, reason: String?) {
	val sanction = Sanction(type, reason ?: return, sanctionMessage.author!!.id)
	respond {
		embed {
			autoSanctionEmbed(sanctionMessage, sanction)
		}
		
		components {
			addBinButtonDeleteSimilarAds()
		}
	}.also {
		sanctionMessages.add(SanctionMessage(sanctionMessage.getAuthorAsMember()!!, it.message, sanction))
	}
}

suspend fun PublicSlashCommandContext<*>.verificationMessage(verifMessage: Message) {
	respond {
		embed {
			verificationEmbed(verifMessage, verifMessage.channel.asChannelOf())
		}
		
		components {
			addVerificationButton()
			
			publicSelectMenu {
				option("interdit", "banned-ad") {
					emoji("\uD83D\uDEAB")
					label = "Publicité interdite"
					
					action {
						message?.let {
							deleteAllSimilarAds(it)
							it.edit {
								components = mutableListOf()
							}
						}
					}
				}
			}
		}
	}
}
