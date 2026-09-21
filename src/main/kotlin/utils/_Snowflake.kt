package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.VoiceChannelBehavior
import dev.kord.core.entity.GuildEmoji
import dev.kord.core.entity.KordEntity
import dev.kord.core.entity.ReactionEmoji

private val CHANNEL_MENTION_CHARS = Regex("[<>#]")
private val USER_MENTION_CHARS = Regex("[<>@!]")
private val ROLE_MENTION_CHARS = Regex("[<>@&]")
private val EMOJI_MENTION_REGEX = Regex("<a?:.+?:(\\d+)>")

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

fun Snowflake.Companion.fromChannelMention(channel: String) = Snowflake(channel.remove(CHANNEL_MENTION_CHARS))
fun Snowflake.Companion.fromUserMention(user: String) = Snowflake(user.remove(USER_MENTION_CHARS))
fun Snowflake.Companion.fromRoleMention(role: String) = Snowflake(role.remove(ROLE_MENTION_CHARS))
fun Snowflake.Companion.fromEmojiMention(emoji: String) = Snowflake(emoji.replace(EMOJI_MENTION_REGEX, "$1"))

fun Snowflake.Companion.fromMessageLink(link: String): Pair<Snowflake, Snowflake> {
	val parts = link.split("/")
	return Snowflake(parts[parts.lastIndex - 1]) to Snowflake(parts.last())
}
