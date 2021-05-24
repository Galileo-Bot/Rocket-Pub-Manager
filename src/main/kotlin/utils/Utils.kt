package utils

import com.kotlindiscord.kord.extensions.commands.CommandContext
import dev.kord.core.Kord
import dev.kord.core.entity.Invite
import dev.kord.core.event.message.MessageCreateEvent

suspend fun getInvite(kord: Kord, text: String): Invite? = kord.getInvite(text, true)

fun findInviteLink(text: String): String? = Regex(DISCORD_INVITE_LINK_REGEX).find(text)?.value
fun findInviteCode(text: String): String? = Regex(DISCORD_INVITE_LINK_REGEX).find(text)?.groupValues?.get(1)

fun isNotBot(event: MessageCreateEvent): Boolean = if (event.member != null) !event.member!!.isBot else false
suspend fun isNotBot(context: CommandContext): Boolean {
	return if (context.getUser() != null) !context.getUser()!!.asUser().isBot else false
}

