package utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Member
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance

fun <T : Channel> Flow<T>.getTextChannels() = filterIsInstance<TextChannel>()

fun <E> MutableList<E>.getFromValue(old: E) = this[indexOf(old)]

fun Member.hasRole(role: Snowflake) = roleIds.contains(role)

suspend fun ReactionEmoji.toGuildEmoji(kord: Kord) = kord.getGuild(ROCKET_PUB_GUILD)?.getEmoji(Snowflake(this))

fun String.cutFormatting(index: Int) = if (length > index - 3) take(index - 3) + "..." else this

fun String.remove(regex: Regex) = replace(regex, "")
fun String.remove(pattern: String) = replace(Regex(pattern), "")

val ReactionEmoji.id get() = urlFormat.remove("$name:")

val String?.enquote get() = this?.let { "'${replace("'", "''")}'" }

val String.asSafeUsersMentions get() = replace(Regex("(<@)!?(\\d{17,19}>)"), "$1$2")

fun Any.prettyPrint(): String {
	var indentLevel = 0
	val indentWidth = 4
	
	fun padding() = "".padStart(indentLevel * indentWidth)
	
	val toString = toString()
	
	val stringBuilder = StringBuilder(toString.length)
	
	var i = 0
	while (i < toString.length) {
		when (val char = toString[i]) {
			'(', '[', '{' -> {
				indentLevel++
				stringBuilder.appendLine(char).append(padding())
			}
			')', ']', '}' -> {
				indentLevel--
				stringBuilder.appendLine().append(padding()).append(char)
			}
			',' -> {
				stringBuilder.appendLine(char).append(padding())
				val nextChar = toString.getOrElse(i + 1) { char }
				if (nextChar == ' ') i++
			}
			else -> {
				stringBuilder.append(char)
			}
		}
		i++
	}
	
	return stringBuilder.toString()
}