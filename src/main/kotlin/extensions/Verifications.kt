package extensions

import debug
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kordex.core.checks.hasRole
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.components.ComponentContainer
import dev.kordex.core.components.publicButton
import dev.kordex.core.components.types.emoji
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralMessageCommand
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
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
			description = "Permets de voir les vérifications du staff."

			publicSubCommand {
				name = "list"
				description = "Permets de voir les vérifications du staff."

				action {
					val verificationCount = getVerificationCount()
					val verifications =
						verificationCount.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.map {
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

				val author = message.getAuthorAsMember()
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
					it.sanction.toString(System.getenv("AYFRI_ROCKETMANAGER_PREFIX")).asSafeUsersMentions == event.message.content.asSafeUsersMentions
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
					it.addAdMessage(event.message)
					return@action
				}

				Verification.create(event.message)
			}
		}

		event<MessageDeleteEvent> {
			check { adsCheck() }

			action {
				val eventMessage = event.message ?: return@action

				getReasonForMessage(eventMessage)?.let { reason ->
					sanctionMessages.find {
						it.sanction.member == eventMessage.author!!.id && it.sanction.reason == reason
					}?.let {
						sanctionMessages.getFromValue(it).sanctionMessage =
							updateMessagesInEmbed(it.sanctionMessage, eventMessage)
					}
				}

				Verification.verifications.findNotValidated(eventMessage)?.setDeletedMessage(event.channel.id)
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
