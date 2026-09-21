package extensions

import chatPrefix
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
import dev.kordex.core.utils.scheduling.Scheduler
import entities.Verification
import fr.ayfri.rocketmanager.i18n.Translations
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.sync.withLock
import logger
import storage.SanctionType
import storage.getVerificationCounts
import utils.*

/** Pending verifications and auto-sanction embeds untouched for that long are dropped from memory. */
private val STALE_AFTER = 24.hours
private val PRUNE_INTERVAL = 30.minutes

class Verifications : Extension() {
	override val name = "Verifications"
	private val scheduler = Scheduler()

	override suspend fun setup() {
		// Components only live in the in-memory registry, so the buttons of the verification messages sent
		// before the last restart are only answered once this registers their IDs again.
		Verification.buttons()

		// Only a convenience for the messages predating the fixed IDs, never worth failing the setup for.
		runCatching { Verification.registerPendingMessagesButtons(kord) }
			.onFailure { logger.error(it) { "Failed to register the buttons of the pending verifications." } }

		// The staff never acts on some verifications and some auto-sanctions never get their chat command: without
		// this they pile up for the whole life of the process. A pruned verification is rebuilt from its embed on click.
		scheduler.schedule(PRUNE_INTERVAL, name = "Ads state prune", pollingSeconds = 60, repeat = true) {
			val cutoff = Clock.System.now() - STALE_AFTER
			Verification.verifications.removeIf { it.lastActivityAt < cutoff }
			sanctionMessages.removeIf { it.sanctionMessage.timestamp < cutoff }
		}

		publicSlashCommand {
			name = Translations.Commands.Verifications.name
			description = Translations.Commands.Verifications.description
			staffOnly()

			publicSubCommand {
				name = Translations.Commands.Verifications.List.name
				description = Translations.Commands.Verifications.List.description

				action {
					val verifications = getVerificationCounts().mapNotNull { (staffId, count) ->
						(guild!!.getMemberOrNull(staffId) ?: return@mapNotNull null) to count
					}

					respond {
						completeEmbed(
							client = this@publicSubCommand.kord,
							title = Translations.Embeds.Verifications.List.title.translate(),
							description = verifications.joinToString("\n\n") {
								Translations.Embeds.Verifications.List.description.translateNamed(
									"username" to it.first.username,
									"count" to it.second.toString()
								)
							}
						)
					}
				}
			}
		}

		ephemeralMessageCommand {
			name = Translations.Commands.Verifications.ForbiddenAd.name
			staffOnly()

			action {
				val message = targetMessages.first()
				val author = message.getAuthorAsMember()

				message.delete(Translations.Messages.forbiddenAd.translate())
				respond(Translations.Messages.userSanctionedForbiddenAd.translateNamed("user" to author.mention))
				author.sanctionForbiddenAd(user.id)
			}
		}

		event<MessageCreateEvent> {
			check {
				adsCheck()
				if (debug) hasRole(STAFF_ROLE)
			}

			// The whole handler runs under the lock: an author posting in several channels at once must be grouped
			// into one verification (or one auto-sanction embed), which a concurrent find-or-create would duplicate.
			action {
				val content = event.message.content.asSafeUsersMentions
				sanctionMessages.find { it.sanction.toString(chatPrefix).asSafeUsersMentions == content }?.let {
					sanctionMessages.remove(it)
					val message = it.sanctionMessage.fetchMessageOrNull() ?: return@let
					setSanctionedBy(message, it.sanction)
				}

				val check = checkAd(event.message)

				Verification.lock.withLock {
					check.reason?.let { reason ->
						val sanction = event.member!!.getNextSanctionType()
						if (sanction == SanctionType.LIGHT_WARN) {
							kord.getLogSanctionsChannel().lightSanction(event.member!!, reason, event.message)

							return@action
						}

						autoSanctionMessage(event.message, sanction, reason)
						return@action
					}

					Verification.verifications.find {
						it.author == event.message.author!!.id &&
							!it.isClosed &&
							it.adMessages.none { m -> m.channelId == event.message.channelId } &&
							(it.adContent == event.message.content || Clock.System.now() - it.lastActivityAt < AD_GROUPING_WINDOW)
					}?.let {
						it.addAdMessage(event.message)
						return@action
					}

					Verification.create(event.message, check.invite)
				}
			}
		}

		event<MessageDeleteEvent> {
			check { adsCheck() }

			// Messages aren't cached, so `event.message` is always null: everything is matched on the IDs.
			action {
				val link = messageJumpUrl(event.channelId, event.messageId)

				sanctionMessages.find { link in it.sanctionMessage.sanctionedAdLinks() }?.let {
					markSanctionedAdDeleted(it.sanctionMessage, link)?.let { edited -> it.sanctionMessage = edited }
				}

				Verification.verifications
					.find { !it.isClosed && it.adMessages.any { m -> m.id == event.messageId } }
					?.setDeletedMessage(event.channelId)
			}
		}
	}
}

suspend fun ComponentContainer.addBinButtonDeleteSimilarAdsWithSanction() {
	publicButton {
		emoji("\uD83D\uDDD1")
		label = Translations.Buttons.delete

		action {
			message.removeComponents()
			message.deleteSanctionedAds()
		}
	}
}
