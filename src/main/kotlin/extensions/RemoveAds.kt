package extensions

import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.core.event.guild.MemberLeaveEvent
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import utils.ROCKET_PUB_GUILD
import utils.getTextChannels
import utils.isInAdChannel


class RemoveAds : Extension() {
	override val name: String = "Remove ads"
	
	@OptIn(InternalCoroutinesApi::class)
	override suspend fun setup() {
		event<MemberLeaveEvent> {
			check {
				isNotBot()
				inGuild(ROCKET_PUB_GUILD)
			}
			
			action {
				run {
					event.guild.channels.getTextChannels().filter(::isInAdChannel).collect { channel ->
						channel.messages.filter { it.author!! == event.user }.collect { it.delete() }
					}
				}
			}
		}
	}
}