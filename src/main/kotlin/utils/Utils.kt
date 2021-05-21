package utils

import dev.kord.core.event.message.MessageCreateEvent

fun findInviteLink(text: String): String? = Regex(DISCORD_INVITE_LINK_REGEX).find(text)?.value

fun isNotBot(event: MessageCreateEvent): Boolean = if (event.member != null) !event.member!!.isBot else false

