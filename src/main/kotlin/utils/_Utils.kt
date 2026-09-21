package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member

private val USER_MENTION_REGEX = Regex("(<@)!?(\\d{17,19}>)")

fun Member.hasRole(role: Snowflake) = roleIds.contains(role)

fun String.cutFormatting(index: Int) = if (length > index - 3) take(index - 3) + "..." else this

fun String.remove(regex: Regex) = replace(regex, "")

/** Compared on every ad against the auto-sanction embeds, compiled once. */
val String.asSafeUsersMentions get() = replace(USER_MENTION_REGEX, "$1$2")
