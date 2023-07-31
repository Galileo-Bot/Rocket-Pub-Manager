package extensions

import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import dev.kord.core.behavior.UserBehavior
import utils.STAFF_ROLE
import utils.asMention

class Errors : Extension() {
	override val name = "errors"

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "test"
			description = "tamer"

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
