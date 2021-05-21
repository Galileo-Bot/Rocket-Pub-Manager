package extensions

import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import dev.kord.core.event.guild.MemberLeaveEvent
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import utils.ROCKET_PUB_GUILD
import utils.getTextChannels
import utils.isAdChannel
import utils.isNotBot

class RemoveAds : Extension() {
	override val name: String = "Remove ads"
	
	@OptIn(InternalCoroutinesApi::class)
	override suspend fun setup() {
		event<MemberLeaveEvent> {
			check(::isNotBot, inGuild(ROCKET_PUB_GUILD))
			
			action {
				
				run {
					event.guild.channels.getTextChannels().filter { isAdChannel(it) }.collect { channel ->
						channel.messages.filter { it.author!! == event.user }.collect { it.delete() }
					}
				}
			}
		}
	}
	
}
