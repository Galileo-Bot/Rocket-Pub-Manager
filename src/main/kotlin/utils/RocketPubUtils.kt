package utils

import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.flow.first


suspend fun isInAdChannel(event: MessageCreateEvent): Boolean = isAdChannel(event.message.channel.asChannel())

fun isAdChannel(channel: Channel): Boolean {
	return (channel is TextChannel
		&& channel.topic != null
		&& channel.topic!!.contains(VALIDATION_EMOJI))
}

suspend fun isInCategoryAdChannel(event: MessageCreateEvent): Boolean {
	val channel = event.message.channel.asChannel()
	return (channel is TextChannel
		&& channel.topic != null
		&& channel.topic!!.contains(VALIDATION_EMOJI_2))
}

suspend fun getLogChannel(event: MessageCreateEvent): TextChannel {
	return event.getGuild()!!.channels.first { it.id == SANCTION_LOGGER_CHANNEL } as TextChannel
}

const val DISCORD_INVITE_LINK_REGEX = "(?:https?:\\/\\/)?(?:\\w+\\.)?discord(?:(?:app)?\\.com\\/invite|\\.gg)\\/([A-Za-z0-9-]+)"
const val VALIDATION_EMOJI_2 = "🔗"
const val VALIDATION_EMOJI = "<:validate:525405975289659402>"
val ROCKET_PUB_GUILD = dev.kord.common.entity.Snowflake("465918902254436362")
val STAFF_ROLE = dev.kord.common.entity.Snowflake("494521544618278934")
val SANCTION_LOGGER_CHANNEL = dev.kord.common.entity.Snowflake("779115065001115649")
