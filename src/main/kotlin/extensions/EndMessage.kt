package extensions

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kordex.core.checks.inGuild
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.deleteIgnoringNotFound
import endMessageAutomatic
import kotlinx.coroutines.flow.firstOrNull
import utils.ROCKET_PUB_GUILD
import utils.endAdChannelEmbed
import utils.isAdChannel
import java.util.concurrent.ConcurrentHashMap

/** The previous end message sits right above the new ad, a few messages back at most when several ads were posted at once. */
private const val END_MESSAGE_LOOKBACK = 20

/**
 * Keeps the "end of channel" embed below the last ad of every ad channel.
 *
 * The embed the bot last sent in each channel is remembered so moving it costs a delete and a create; the channel
 * history is only looked at, and only its last few messages, for the first ad of a channel since the last restart.
 */
class EndMessage : Extension() {
	override val name = "End-Message"
	private val lastEndMessages = ConcurrentHashMap<Snowflake, Snowflake>()

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
				val message = event.message
				val channel = message.getChannel().asChannelOf<TextChannel>()

				val previous = lastEndMessages[channel.id]?.let { MessageBehavior(channel.id, it, kord) }
					?: channel.getMessagesBefore(message.id, END_MESSAGE_LOOKBACK)
						.firstOrNull { it.author?.id == kord.selfId && it.embeds.firstOrNull()?.author?.name == channel.getGuild().name }

				previous?.deleteIgnoringNotFound()

				lastEndMessages[channel.id] = channel.createEmbed { endAdChannelEmbed(kord, channel) }.id
			}
		}
	}
}
