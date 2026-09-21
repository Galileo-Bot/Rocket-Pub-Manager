package extensions

import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kordex.core.checks.inGuild
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.deleteIgnoringNotFound
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import storage.getAdMessages
import utils.ROCKET_PUB_GUILD


/**
 * Deletes the ads of the members leaving the guild.
 *
 * The ads come from the `ad_events` table rather than from the channel histories: scanning every ad channel costs
 * one request per 100 messages of history, deleting by ID costs one request per ad, and nothing when there is none.
 */
class RemoveAds : Extension() {
	override val name: String = "Remove-Ads"

	override suspend fun setup() {
		event<MemberLeaveEvent> {
			check {
				isNotBot()
				inGuild(ROCKET_PUB_GUILD)
			}

			action {
				val ads = getAdMessages(event.user.id)

				// Each channel has its own rate-limit bucket, so the deletions run in parallel.
				coroutineScope {
					ads.forEach { launch { MessageBehavior(it.channelID, it.messageID, kord).deleteIgnoringNotFound() } }
				}
			}
		}
	}
}
