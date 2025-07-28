package extensions

import dev.kord.core.behavior.UserBehavior
import dev.kordex.core.checks.hasRole
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import fr.ayfri.rocketmanager.i18n.Translations
import utils.STAFF_ROLE
import utils.asMention

class Errors : Extension() {
	override val name = "errors"

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = Translations.Commands.Errors.Test.name
			description = Translations.Commands.Errors.Test.description

			check {
				hasRole(STAFF_ROLE)
			}

			action {
				respond(user.id.asMention<UserBehavior>())

				throw IllegalStateException("test made by ${user.asUser().username}")
			}
		}
	}
}
