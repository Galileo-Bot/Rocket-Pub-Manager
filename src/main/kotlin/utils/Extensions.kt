package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.entity.GuildEmoji
import dev.kord.core.entity.Member
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance

fun <T> Snowflake(value: T): Snowflake {
	return when (value) {
		is ChannelBehavior -> Snowflake.forChannel(value.mention)
		is UserBehavior -> Snowflake.forUser(value.mention)
		is RoleBehavior -> Snowflake.forRole(value.mention)
		is ReactionEmoji -> Snowflake.forEmoji(value.mention)
		is GuildEmoji -> value.id
		else -> Snowflake(value.toString())
	}
}

fun Snowflake.Companion.forChannel(channel: String) = Snowflake(channel.removeMatches("[<>#]"))
fun Snowflake.Companion.forUser(user: String) = Snowflake(user.removeMatches("[<>@!]"))
fun Snowflake.Companion.forRole(role: String) = Snowflake(role.removeMatches("[<>@&]"))
fun Snowflake.Companion.forEmoji(emoji: String) = Snowflake(emoji.replace("<a?:.+?:(\\d+)>", "$1"))

fun String.removeMatches(regex: Regex) = replace(regex, "")
fun String.removeMatches(pattern: String) = replace(Regex(pattern), "")

fun <E> MutableList<E>.getFromValue(old: E) = this[indexOf(old)]

fun <T : Channel> Flow<T>.getTextChannels() = filterIsInstance<TextChannel>()

suspend fun ReactionEmoji.toGuildEmoji(kord: Kord) = kord.getGuild(ROCKET_PUB_GUILD)?.getEmoji(Snowflake(this))

fun Member.hasRole(role: Snowflake) = roleIds.contains(role)

val String?.enquote get() = this?.let { "'${replace("'", "''")}'" }

val ReactionEmoji.id get() = urlFormat.removeMatches("$name:")

val String.asSafeUsersMentions get() = replace(Regex("(<@)!?(\\d{17,19}>)"), "$1$2")
