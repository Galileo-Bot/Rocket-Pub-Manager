package extensions.sanctions

import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralUserCommand
import extensions.lightSanction
import extensions.respond
import extensions.staffOnly
import fr.ayfri.rocketmanager.i18n.Translations
import storage.Sanction
import storage.SanctionType
import utils.applyToMember
import utils.getLogSanctionsChannel
import utils.replyWithSanctionEmbed
import utils.sendLog

/** A ban from the context menu wipes the member's last week of messages, the most Discord allows. */
private const val BAN_DELETE_DAYS = 7

class UserContextSanctions : Extension() {
	override val name = "UserContextSanctions"

	inner class ModalArguments : ModalForm() {
		override var title = Translations.Modal.Sanction.title

		val reason = paragraphText {
			label = Translations.Modal.Sanction.reasonLabel
			maxLength = 500
			minLength = 3
			placeholder = Translations.Modal.Sanction.reasonPlaceholder
			required = true
		}
	}


	override suspend fun setup() {
		val commands = mapOf(
			SanctionType.BAN to Translations.Commands.UserContext.Ban.name,
			SanctionType.KICK to Translations.Commands.UserContext.Kick.name,
			SanctionType.LIGHT_WARN to Translations.Commands.UserContext.LightWarn.name,
			SanctionType.WARN to Translations.Commands.UserContext.Warn.name,
		)

		commands.forEach { (sanctionType, commandName) ->
			ephemeralUserCommand(::ModalArguments) {
				name = commandName
				staffOnly()

				action { modal ->
					val reason = modal?.reason?.value
					if (reason.isNullOrBlank()) {
						respond(Translations.Errors.pleaseFillForm.translate())
						return@action
					}

					val target = event.interaction.target.asMember(guild!!.id)
					val sanction = Sanction(sanctionType, reason, target.id, appliedBy = user.id)

					// The light warn records itself, and its message in the logs channel stands in for the sanction log.
					if (sanctionType == SanctionType.LIGHT_WARN) {
						event.kord.getLogSanctionsChannel().lightSanction(target, reason, appliedBy = user.id)
					} else {
						if (sanctionType != SanctionType.WARN) sanction.applyToMember(target, banDeleteDays = BAN_DELETE_DAYS)
						sanction.save()
						sanction.sendLog(event.kord)
					}

					replyWithSanctionEmbed(sanction)
				}
			}
		}
	}
}
