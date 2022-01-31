package extensions

import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.deleteIgnoringNotFound
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.event.message.MessageCreateEvent
import endMessageAutomatic
import kotlinx.coroutines.flow.filter
import utils.ROCKET_PUB_GUILD
import utils.endAdChannelEmbed
import utils.isAdChannel

class EndMessage : Extension() {
	override val name = "End-Message"
	
	override suspend fun setup() {
		event<MessageCreateEvent> {
			check {
				inGuild(ROCKET_PUB_GUILD)
				isAdChannel()
				isNotBot()
				
				if (!passed) return@check
				
				failIf { !endMessageAutomatic }
			}
			
			action {
				event.message.channel.messages.filter {
					it.author?.isBot == true && (it.embeds[0].author?.name ?: return@filter false) in event.message.getGuild().name
				}.collect {
					it.deleteIgnoringNotFound()
				}
				
				event.message.channel.createEmbed {
					endAdChannelEmbed(kord, event.message.channel.fetchChannel().asChannelOf())
				}
			}
		}
	}
}
