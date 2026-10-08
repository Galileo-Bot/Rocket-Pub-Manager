package extensions.sanctions

import dev.kord.rest.builder.component.SelectOptionBuilder
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralUserCommand
import extensions.staffOnly
import fr.ayfri.rocketmanager.i18n.Translations
import storage.Sanction
import storage.SanctionType
import utils.getNextMuteDuration
import utils.getNextSanctionType
import utils.issue
import utils.replyWithSanctionEmbed
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A ban from the context menu wipes the member's last week of messages, the most Discord allows. */
private const val BAN_DELETE_DAYS = 7

/** Value of the type option that applies the next step of the escalation, as `/sanctions infos` announces it. */
private const val ESCALATION = "escalation"

class SanctionModal : ModalForm() {
	override var title = Translations.Modal.Sanction.title

	init {
		textDisplay { content = Translations.Modal.Sanction.help }
	}

	val type = stringSelect {
		label = Translations.Modal.Sanction.typeLabel
		options += SelectOptionBuilder(Translations.Modal.Sanction.escalation.translate(), ESCALATION).apply { default = true }
		SanctionType.entries.forEach { options += SelectOptionBuilder(it.translation.translate(), it.name) }
	}

	val presetReason = stringSelect {
		label = Translations.Modal.Sanction.presetLabel
		minValues = 0
		required = false
		sanctionReasons.forEach { (shorthand, reason) -> options += SelectOptionBuilder(reason, shorthand) }
	}

	val reason = paragraphText {
		label = Translations.Modal.Sanction.reasonLabel
		maxLength = 500
		placeholder = Translations.Modal.Sanction.reasonPlaceholder
		required = false
	}

	val duration = lineText {
		label = Translations.Modal.Sanction.durationLabel
		maxLength = 20
		placeholder = Translations.Modal.Sanction.durationPlaceholder
		required = false
	}
}

/** Kotlin's duration format with the French `j` for days: `2h`, `3j`, `1j 12h`. */
private fun parseDuration(text: String) = Duration.parseOrNull(text.trim().lowercase().replace('j', 'd'))
	?: throw DiscordRelayedException(Translations.Errors.invalidDuration)

/** One "Sanctionner" entry in the member context menu, any sanction type, a preset or written reason and an optional duration. */
class UserContextSanctions : Extension() {
	override val name = "UserContextSanctions"

	override suspend fun setup() {
		ephemeralUserCommand(::SanctionModal) {
			name = Translations.Commands.UserContext.Sanction.name
			staffOnly()

			action { modal ->
				modal ?: return@action

				val reason = listOfNotNull(
					modal.presetReason.value.firstOrNull()?.let(sanctionReasons::get),
					modal.reason.value?.takeIf(String::isNotBlank)
				).joinToString(" ").ifEmpty { throw DiscordRelayedException(Translations.Errors.pleaseFillForm) }

				val target = event.interaction.target.asMember(guild!!.id)
				val type = modal.type.value.firstOrNull()?.takeIf { it != ESCALATION }?.let(SanctionType::valueOf)
					?: target.getNextSanctionType()

				// A mute with no duration given follows the escalation, a ban with none is permanent.
				val given = modal.duration.value?.takeIf(String::isNotBlank)?.let(::parseDuration)
				val duration = when (type) {
					SanctionType.MUTE -> given ?: target.getNextMuteDuration().milliseconds
					SanctionType.BAN -> given ?: Duration.ZERO
					else -> Duration.ZERO
				}

				Sanction(type, reason, target.id, appliedBy = user.id, durationMS = duration.inWholeMilliseconds).apply {
					issue(target, BAN_DELETE_DAYS)
					replyWithSanctionEmbed(this)
				}
			}
		}
	}
}
