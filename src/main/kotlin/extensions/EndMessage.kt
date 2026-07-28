package extensions

import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.event.message.MessageCreateEvent
import dev.kordex.core.checks.inGuild
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.deleteIgnoringNotFound
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
				val channel = event.message.channel
				val guildName = event.message.getGuild().name
				channel.messages.filter {
					it.author?.isBot == true && (it.embeds.firstOrNull()?.author?.name
						?: return@filter false) in guildName
				}.collect {
					it.deleteIgnoringNotFound()
				}

				channel.createEmbed {
					endAdChannelEmbed(kord, channel.fetchChannel().asChannelOf())
				}
			}
		}
	}
}
