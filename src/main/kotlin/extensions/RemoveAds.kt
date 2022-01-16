package extensions

import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.deleteIgnoringNotFound
import dev.kord.core.event.guild.MemberLeaveEvent
import kotlinx.coroutines.flow.filter
import utils.ROCKET_PUB_GUILD
import utils.getTextChannels
import utils.isAdChannel


class RemoveAds : Extension() {
	override val name: String = "Remove ads"
	
	override suspend fun setup() {
		event<MemberLeaveEvent> {
			check {
				isNotBot()
				inGuild(ROCKET_PUB_GUILD)
			}
			
			action {
				event.guild.channels.getTextChannels().filter { isAdChannel(it) }.collect { channel ->
					channel.messages.filter {
						it.author?.fetchUserOrNull() == event.user
					}.collect {
						it.deleteIgnoringNotFound()
					}
				}
			}
		}
	}
}
