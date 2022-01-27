package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.VoiceChannelBehavior
import dev.kord.core.entity.GuildEmoji
import dev.kord.core.entity.KordEntity
import dev.kord.core.entity.ReactionEmoji

fun <T> Snowflake(value: T) = when (value) {
	is ChannelBehavior -> Snowflake.forChannel(value.mention)
	is UserBehavior -> Snowflake.forUser(value.mention)
	is RoleBehavior -> Snowflake.forRole(value.mention)
	is ReactionEmoji -> Snowflake.forEmoji(value.mention)
	is GuildEmoji -> value.id
	else -> Snowflake(value.toString())
}

inline fun <reified T : KordEntity> Snowflake.toMention() = when (T::class) {
	is VoiceChannelBehavior -> "<#!${this}>"
	is ChannelBehavior -> "<#${this}>"
	is UserBehavior -> "<@${this}>"
	is RoleBehavior -> "<@&${this}>"
	else -> toString()
}

fun Snowflake.Companion.forChannel(channel: String) = Snowflake(channel.remove("[<>#]"))
fun Snowflake.Companion.forUser(user: String) = Snowflake(user.remove("[<>@!]"))
fun Snowflake.Companion.forRole(role: String) = Snowflake(role.remove("[<>@&]"))
fun Snowflake.Companion.forEmoji(emoji: String) = Snowflake(emoji.replace("<a?:.+?:(\\d+)>", "$1"))

val Snowflake?.enquote get() = toString().enquote
