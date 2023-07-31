package extensions

import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.components.ComponentContainer
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralMessageCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import configuration
import debug
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import entities.Verification
import entities.findNotValidated
import storage.Sanction
import storage.SanctionType
import storage.getVerificationCount
import utils.*

class Verifications : Extension() {
	override val name = "Verifications"

	override suspend fun setup() {
		publicSlashCommand {
			name = "verif"
			description = "Permet de voir les vérifications du staff."

			publicSubCommand {
				name = "list"
				description = "Permet de voir les vérifications du staff."

				action {
					val verificationCount = getVerificationCount()
					val verifications = verificationCount.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.map {
						(guild!!.getMemberOrNull(it.first) ?: return@map null) to it.second
					}.filterNotNull()

					respond {
						completeEmbed(
							client = this@publicSubCommand.kord,
							title = "Liste des publicités vérifiées.",
							description = verifications.joinToString("\n\n") {
								"**${it.first.username}** : ${it.second} publicités vérifiées."
							}
						)
					}
				}
			}
		}

		ephemeralMessageCommand {
			name = "pub-interdite"
			guild(ROCKET_PUB_GUILD)

			action {
				val type = user.getNextSanctionType()
				val message = targetMessages.elementAt(0)

				val author = message.getAuthorAsMemberOrThrow()
				message.delete("Publicité interdite.")
				Sanction(
					type,
					"Publicité interdite.",
					author.id,
					user.fetchUserOrNull()?.id,
					if (type == SanctionType.MUTE) author.getNextMuteDuration() else 0
				).apply {
					respond("${author.mention} a été sanctionné pour avoir publié une publicité interdite.")

					applyToMember(author)
					sendLog(message.kord)
					save()
				}
			}
		}

		event<MessageCreateEvent> {
			check {
				adsCheck()
				if (debug) hasRole(STAFF_ROLE)
			}

			action {
				sanctionMessages.find {
					it.sanction.toString(configuration["AYFRI_ROCKETMANAGER_PREFIX"]).asSafeUsersMentions == event.message.content.asSafeUsersMentions
				}?.let {
					sanctionMessages.remove(it)
					val message = it.sanctionMessage.fetchMessageOrNull() ?: return@let
					setSanctionedBy(message, it.sanction)
				}

				getReasonForMessage(event.message)?.let { reason ->
					val sanction = event.member!!.getNextSanctionType()
					if (sanction == SanctionType.LIGHT_WARN) {
						kord.getLogSanctionsChannel().lightSanction(event.member!!, reason, event.message)

						return@action
					}

					autoSanctionMessage(event.message, sanction, reason)
					return@action
				}

				Verification.verifications.find {
					it.adContent == event.message.content && it.author == event.message.author!!.id
				}?.let {
					it.addAdChannel(event.message)
					return@action
				}

				Verification.create(event.message)
			}
		}

		event<MessageDeleteEvent> {
			check { adsCheck() }

			action {
				getReasonForMessage(event.message ?: return@action)?.let { reason ->
					sanctionMessages.find {
						it.sanction.member == event.message!!.author!!.id && it.sanction.reason == reason
					}?.let {
						sanctionMessages.getFromValue(it).sanctionMessage = updateChannels(it.sanctionMessage, event.channel)
					}
				}

				Verification.verifications.findNotValidated(event.message ?: return@action)?.setDeletedChannel(event.channel.id)
			}
		}
	}
}

suspend fun ComponentContainer.addBinButtonDeleteSimilarAdsWithSanction() {
	publicButton {
		emoji("\uD83D\uDDD1")
		label = "Supprimer"

		action {
			message.removeComponents()
			deleteAllSimilarAdsWithSanction(message)
		}
	}
}
