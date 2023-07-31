package extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import dev.kord.core.behavior.UserBehavior
import utils.toMention

class Errors : Extension() {
	override val name = "errors"

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "test"
			description = "tamer"

			action {
				respond(user.id.toMention<UserBehavior>())

				throw IllegalStateException("test")
			}
		}
	}
}
