package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.VoiceChannelBehavior
import dev.kord.core.entity.KordEntity

private val CHANNEL_MENTION_CHARS = Regex("[<>#]")

inline fun <reified T : KordEntity> Snowflake.asMention() = when (T::class) {
	VoiceChannelBehavior::class -> "<#!$this>"
	ChannelBehavior::class -> "<#$this>"
	UserBehavior::class -> "<@$this>"
	RoleBehavior::class -> "<@&$this>"
	else -> toString()
}

fun Snowflake.Companion.fromChannelMention(channel: String) = Snowflake(channel.remove(CHANNEL_MENTION_CHARS))

fun Snowflake.Companion.fromMessageLink(link: String): Pair<Snowflake, Snowflake> {
	val parts = link.split("/")
	return Snowflake(parts[parts.lastIndex - 1]) to Snowflake(parts.last())
}
