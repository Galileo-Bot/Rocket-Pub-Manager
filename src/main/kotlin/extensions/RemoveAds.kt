package extensions

import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kordex.core.checks.inGuild
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.deleteIgnoringNotFound
import kotlinx.coroutines.flow.filter
import utils.ROCKET_PUB_GUILD
import utils.getTextChannels
import utils.isAdChannel


class RemoveAds : Extension() {
	override val name: String = "Remove-Ads"

	override suspend fun setup() {
		event<MemberLeaveEvent> {
			check {
				isNotBot()
				inGuild(ROCKET_PUB_GUILD)
			}

			action {
				event.guild.channels.getTextChannels().filter(TextChannel::isAdChannel).collect { channel ->
					channel.messages.filter {
						it.author?.id == event.user.id
					}.collect {
						it.deleteIgnoringNotFound()
					}
				}
			}
		}
	}
}
