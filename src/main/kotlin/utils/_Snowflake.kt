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
	is ChannelBehavior -> Snowflake.fromChannelMention(value.mention)
	is UserBehavior -> Snowflake.fromUserMention(value.mention)
	is RoleBehavior -> Snowflake.fromRoleMention(value.mention)
	is ReactionEmoji -> Snowflake.fromEmojiMention(value.mention)
	is GuildEmoji -> value.id
	else -> Snowflake(value.toString())
}

inline fun <reified T : KordEntity> Snowflake.asMention() = when (T::class) {
	VoiceChannelBehavior::class -> "<#!$this>"
	ChannelBehavior::class -> "<#$this>"
	UserBehavior::class -> "<@$this>"
	RoleBehavior::class -> "<@&$this>"
	else -> toString()
}

fun Snowflake.Companion.fromChannelMention(channel: String) = Snowflake(channel.remove("[<>#]"))
fun Snowflake.Companion.fromUserMention(user: String) = Snowflake(user.remove("[<>@!]"))
fun Snowflake.Companion.fromRoleMention(role: String) = Snowflake(role.remove("[<>@&]"))
fun Snowflake.Companion.fromEmojiMention(emoji: String) = Snowflake(emoji.replace("<a?:.+?:(\\d+)>", "$1"))

fun Snowflake.Companion.fromMessageLink(link: String) = Snowflake(link.split("/").dropLast(1).last()) to Snowflake(link.split("/").last())

val Snowflake?.enquote get() = toString().enquote
