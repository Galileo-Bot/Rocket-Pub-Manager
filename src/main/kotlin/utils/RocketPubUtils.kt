package utils

import com.kotlindiscord.kord.extensions.checks.channelFor
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.events.EventContext
import configuration
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.Event
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.flow.first

const val DISCORD_INVITE_LINK_REGEX = "(?:https?:\\/\\/)?(?:\\w+\\.)?discord(?:(?:app)?\\.com\\/invite|\\.gg)\\/([A-Za-z0-9-]+)"
const val VALIDATION_EMOJI_2 = "🔗"
const val VALIDATION_EMOJI = "<:validate:525405975289659402>"
val VALID_EMOJI = Snowflake("525406069913157641")
val ROCKET_PUB_GUILD = Snowflake("465918902254436362")
val STAFF_ROLE = Snowflake("494521544618278934")
val SANCTION_LOGGER_CHANNEL = Snowflake(configuration["AYFRI_ROCKETMANAGER_LOGCHANNEL_ID"])

fun isInCategoryAdChannel(channel: ChannelBehavior) = channel is TextChannel && channel.topic?.contains(VALIDATION_EMOJI) == true
fun isInAdChannel(channel: ChannelBehavior) = channel is TextChannel && channel.topic?.contains(VALIDATION_EMOJI_2) == true

suspend fun <T : Event> CheckContext<T>.isInAdChannel() {
	if (!passed) return
	passIf { isInAdChannel(channelFor(event) ?: return@passIf false) }
}

suspend fun <T : Event> CheckContext<T>.isInAdCategoryChannel() {
	if (!passed) return
	passIf { isInCategoryAdChannel(channelFor(event) ?: return@passIf false) }
}

suspend fun EventContext<MessageCreateEvent>.getLogChannel() = event.getGuild()!!.channels.first {
	it.id == SANCTION_LOGGER_CHANNEL
} as TextChannel
