package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Channel

fun <T> Snowflake(value: T) {
	when (value) {
		is Channel -> Snowflake.forChannel(value.mention)
		is User -> Snowflake.forUser(value.mention)
		is Member -> Snowflake.forMember(value.mention)
		is Role -> Snowflake.forRole(value.mention)
	}
}

fun Snowflake.Companion.forChannel(channel: String) = Snowflake(channel.removeMatches("[<>#]"))
fun Snowflake.Companion.forUser(user: String) = Snowflake(user.removeMatches("[<>@!]"))
fun Snowflake.Companion.forMember(member: String) = Snowflake(member.removeMatches("[<>@!]"))
fun Snowflake.Companion.forRole(role: String)= Snowflake(role.removeMatches("[<>@&]"))


fun String.removeMatches(regex: Regex) = replace(regex, "")
fun String.removeMatches(pattern: String) = replace(Regex(pattern), "")

fun <E> MutableList<E>.getFromValue(old: E): E = this[indexOf(old)]

val String.asSafeUsersMentions: String
	get() = replace(Regex("(<@)!?(\\d{17,19}>)"), "$1$2")
