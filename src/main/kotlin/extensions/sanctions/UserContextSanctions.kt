package extensions.sanctions

import dev.kord.core.behavior.ban
import dev.kordex.core.checks.inGuild
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralUserCommand
import extensions.lightSanction
import extensions.respond
import fr.ayfri.rocketmanager.i18n.Translations
import storage.Sanction
import storage.SanctionType
import utils.ROCKET_PUB_GUILD
import utils.getLogSanctionsChannel
import utils.replyWithSanctionEmbed
import utils.sendLog
import kotlin.time.Duration.Companion.days

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
		val userCommandsSanctionTypes = listOf("ban", "kick", "light_warn", "warn")
		userCommandsSanctionTypes.forEach { commandName ->
			ephemeralUserCommand(::ModalArguments) {
				name = when (commandName) {
					"ban" -> Translations.Commands.UserContext.Ban.name
					"kick" -> Translations.Commands.UserContext.Kick.name
					"light_warn" -> Translations.Commands.UserContext.LightWarn.name
					"warn" -> Translations.Commands.UserContext.Warn.name
					else -> throw IllegalArgumentException("Unknown command name: $commandName")
				}
				guildId = ROCKET_PUB_GUILD

				check {
					inGuild(ROCKET_PUB_GUILD)
				}

				action { modal ->
					if (modal == null || modal.reason.value.isNullOrBlank()) {
						respond(Translations.Errors.pleaseFillForm.translate())
						return@action
					}

					val sanctionType = SanctionType.valueOf(commandName.uppercase())
					val reason = modal.reason.value!!
					val target = event.interaction.target.asMember(guild!!.id)
					val author = event.interaction.user

					Sanction(sanctionType, reason, target.id, appliedBy = author.id).apply {
						val kord = this@ephemeralUserCommand.kord

						replyWithSanctionEmbed(this)
						save()
						sendLog(kord)

						when (sanctionType) {
							SanctionType.LIGHT_WARN -> {
								kord.getLogSanctionsChannel().lightSanction(target, reason)
								return@apply
							}

							SanctionType.BAN -> target.ban {
								this.reason = reason
								deleteMessageDuration = 7.days
							}

							SanctionType.KICK -> target.kick(reason)

							else -> {}
						}
					}
				}
			}
		}
	}
}
